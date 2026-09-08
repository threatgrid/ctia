(ns ctia.domain.url-safety
  "Pure predicates backing the ingest URL-scheme gate (XFV-135), extracted from
   ctia.flows.crud so the flow keeps only the thin `url-scheme-check` wrapper and
   the security logic is unit-testable in isolation (mirroring how tlp/access-
   control checks delegate to ctia.domain.access-control).

   Defense-in-depth against stored XSS (CWE-79/CWE-116) via threat-intel URL
   fields: a value in a URL-typed field whose scheme is not http(s) is reported
   so the flow can reject it before it reaches a UI render sink. The primary XSS
   control remains output-encoding at that sink, owned by the UI team; this is
   the storage-layer backstop."
  (:require
   [clojure.string :as str]
   [ctia.schemas.core :as schemas])
  (:import
   java.util.Locale))

(def safe-url-schemes
  "Allowlist of URL schemes permitted in URL-typed fields on ingest. Any other
   scheme (javascript:, data:, vbscript:, ...) is rejected as defense-in-depth
   against stored XSS via threat-intel URL fields (XFV-135). A value is
   scheme-checked only when its prefix matches the RFC 3986 scheme grammar
   (see `url-scheme-re`); truly scheme-less / relative values (e.g. \"/a/b\",
   \"example.com/a\") carry no scheme and pass. An ambiguous bare authority such
   as \"example.com:8080/path\" matches the RFC 3986 scheme grammar with scheme
   \"example.com\" (java.net.URI/create accepts it as an absolute URI) and is
   rejected as an unknown scheme, which is acceptable: CTIM URI-typed fields
   carry absolute http(s) URLs, not bare host:port authorities."
  #{"http" "https"})

(def url-typed-keys
  "Entity keys whose values may be CTIM URI-typed and are rendered as clickable
   hyperlinks by downstream UIs. Kept in sync with the URI-typed entries across
   CTIM schemas (:url, :source_uri, :origin_uri, :reason_uri). `:identity` is
   included defensively: it is URI-typed only in RelatedIdentity (:identity a
   URI ref) and is a nested map elsewhere (actor.cljc, identity_assertion.cljc);
   a non-string :identity value is recursed into, not flagged (see
   `collect-unsafe-url-fields`). This is a cross-schema hand-curation, not the
   key set of a single schema.
   These are the render-sinks reachable by an attacker via CTIA writes; free-text
   fields and observable IOC values (which legitimately carry malicious URLs) are
   intentionally NOT in this set. Matching is by key name at any nesting depth,
   which assumes every field so named in a CTIM object is a URI-typed http(s)
   field; a future same-named free-text field would need adding here."
  #{:url :source_uri :origin_uri :reason_uri :identity})

;; RFC 3986 scheme: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) followed by ":"
(def url-scheme-re #"(?i)^([a-z][a-z0-9+.\-]*):")

(defn code-point->str
  "Unicode code point -> String, or nil when out of range."
  [n]
  (when (<= 0 n 0x10FFFF)
    (String. (Character/toChars n))))

(def scheme-relevant-named-entities
  "The HTML named character references that can help reconstruct a URL scheme at
   the render sink, mapped to the character they decode to:
     - \"amp\"     -> \"&\"  the entity-delimiter itself: `&amp;` is the canonical
                             encoding of `&`, so `javascript&amp;colon;alert(1)`
                             HTML-decodes to `javascript&colon;alert(1)` and then
                             to `javascript:alert(1)`. Including it lets the
                             fixed-point loop (see `decode-html-entities`) unmask
                             this double-encoded delimiter, closing the gap its
                             numeric twin `&#38;colon;` already covered.
     - \"colon\"   -> \":\"  the scheme delimiter, the one structural char with a
                             named reference: `javascript&colon;alert(1)`.
     - \"tab\"     -> \"\\t\" and
     - \"newline\" -> \"\\n\" whitespace the WHATWG URL parser strips from anywhere
                             in a URL, so `java&NewLine;script:...` -> `javascript:`.
   No HTML5 named reference decodes to a single ASCII *letter* usable to spell a
   scheme name (the sole ASCII-letter-producing entity, &fjlig; -> \"fj\", cannot
   form any executable scheme), so an attacker can only hide the `&`/`:` delimiters
   or insert tab/newline between the letters -- this closed, scheme-relevant set is
   what the gate decodes. Named references that decode to a non-scheme, non-parser-
   stripped character (e.g. &ZeroWidthSpace; -> U+200B, which the WHATWG URL parser
   does NOT strip from a scheme) cannot reconstruct an executable scheme and are
   deliberately left encoded. Matched case-insensitively since over-decoding can
   only reveal a scheme, never create a false positive on a legitimate http(s) URL."
  {"amp" "&" "colon" ":" "tab" "\t" "newline" "\n"})

(defn decode-html-entities
  "Decodes the HTML character references an attacker could use to obfuscate a URL
   scheme, so the scheme is unmasked before extraction: numeric (&#NN;), hex
   (&#xHH;), and the scheme-relevant NAMED references (&amp; &colon; &Tab;
   &NewLine;; see `scheme-relevant-named-entities`). Decoding can only *reveal* a
   scheme (e.g. \"javascript&colon;...\" -> \"javascript:...\"); it never turns a
   legitimate http(s) URL into a dangerous one -- the scheme sits at the front and
   is extracted first, so a decoded `&amp;`/`&colon;` in a query string cannot
   change it. It CAN surface a scheme on a *scheme-less* value that encoded a colon
   (e.g. a filename \"report&#58;final.pdf\" -> \"report:final.pdf\", rejected as a
   non-http(s) scheme); URI-typed fields are expected to carry absolute http(s)
   URLs, so that rejection is acceptable rather than a false positive on a URL.
   Downstream HTML output-encoding remains the primary XSS control.

   Decoding is iterated to a fixed point under a bounded loop so a reference that
   only becomes visible after an *outer* reference is decoded is still unmasked:
   \"javascript&#38;colon;alert(1)\" -> \"javascript&colon;alert(1)\" ->
   \"javascript:alert(1)\". Each pass peels one encoding layer (a decoded entity
   collapses to a single character, shortening the string) or leaves it byte-
   identical (an unparseable / oversized numeric entity is left untouched, which is
   the loop's exit condition), so it always converges. The budget is load-bearing,
   not merely a safety net: it caps the peel depth, so a value nested deeper than
   the budget (9+ stacked `&#38;`) is left partially-encoded -- which fails safe,
   since a partially-encoded value does not present an http(s)-or-dangerous scheme
   and a browser HTML-decodes only one layer per render, never reaching the payload
   in a single hop."
  [s]
  (letfn [(num-ref [m digits radix]
            (or (some-> (try (Integer/parseInt digits radix)
                             (catch NumberFormatException _ nil))
                        code-point->str)
                ;; unparseable / oversized code point: leave the text untouched
                m))
          (decode-once [s]
            (-> s
                (str/replace #"(?i)&(amp|colon|tab|newline);"
                             (fn [[m nm]]
                               ;; Locale/ROOT: str/lower-case uses the JVM default
                               ;; locale, under which tr/az lowercase "NEWLINE" to
                               ;; "newlıne" (dotless i), missing the map; the `or`
                               ;; also nil-guards so a miss leaves the text untouched
                               ;; rather than NPE-ing str/replace out to a 500.
                               (or (scheme-relevant-named-entities
                                    (.toLowerCase ^String nm Locale/ROOT))
                                   m)))
                (str/replace #"(?i)&#x([0-9a-f]+);?" (fn [[m d]] (num-ref m d 16)))
                (str/replace #"&#([0-9]+);?"         (fn [[m d]] (num-ref m d 10)))))]
    (loop [prev s, budget 8]
      (let [decoded (decode-once prev)]
        (if (or (= decoded prev) (zero? budget))
          decoded
          (recur decoded (dec budget)))))))

(defn unsafe-url-scheme
  "When `v` is a string carrying a URL scheme not in `safe-url-schemes`, returns
   that (lower-cased) scheme; otherwise nil. HTML entities are decoded and
   control/whitespace/format characters stripped before scheme extraction,
   because browsers ignore such characters when resolving a URL (so
   \"java\\tscript:...\", \"&#106;avascript:...\" and \"javascript&colon;...\" all
   resolve to \"javascript:...\"). The strip class covers ASCII C0 controls and
   space (\\x00-\\x20, \\x7f) plus the non-ASCII ignorables the decoder can emit --
   Unicode format chars (\\p{Cf}: ZWSP U+200B, BOM U+FEFF, SOFT HYPHEN U+00AD, ...)
   and separators (\\p{Z}: NBSP U+00A0, ...) -- so \"java&#8203;script:\" and
   \"&#65279;javascript:\" are unmasked, not just their ASCII twins.

   Transient references (`transient:...`) are CTIA-internal placeholders callers
   put in URI-typed fields (e.g. :source_uri) to cross-link entities in a bulk
   submission. They are exempt because `transient:` is not a browser-executable
   scheme -- so even an unresolved `transient:<arbitrary>` is inert at the render
   sink. (CTIA transient IDs are arbitrary client-chosen strings, e.g.
   \"transient:0\", not necessarily UUIDs, and a plain URI field like :source_uri
   is not a reference field the flow rewrites, so the exemption cannot rest on
   rewriting.) This check also runs before transient IDs are resolved (see
   ctia.flows.crud/validate-entities -> create-ids-from-transient)."
  [v]
  (when (and (string? v)
             (not (schemas/transient-id? v)))
    (let [normalized (-> v
                         decode-html-entities
                         (str/replace #"[\x00-\x20\x7f\p{Cf}\p{Z}]" ""))]
      (when-let [raw (some-> (re-find url-scheme-re normalized) second)]
        ;; Locale/ROOT for the same reason as the decoder: str/lower-case would
        ;; fold under the JVM default locale (tr/az dotted-i). No security impact
        ;; today (no safe scheme contains an "i"), but keeps the two lower-casings
        ;; consistent and locale-independent.
        (let [scheme (.toLowerCase ^String raw Locale/ROOT)]
          (when-not (contains? safe-url-schemes scheme)
            scheme))))))

(defn collect-unsafe-url-fields
  "Recursively walks `x` (maps/vectors/sets) and returns a seq of
   {:field <key> :scheme <scheme> :value <string>} for every URL-typed field
   whose value carries a disallowed scheme. Only string values under
   `url-typed-keys` are checked; non-string values (e.g. a nested Identity map
   under :identity) are recursed into rather than flagged. `:value` carries the
   exact offending string so the caller can diff against prev-entity and flag only
   newly-introduced values."
  [x]
  (cond
    (map? x)
    (mapcat (fn [[k v]]
              (concat
               (when (contains? url-typed-keys k)
                 ;; unsafe-url-scheme nil-guards non-strings, so a uniform scan
                 ;; over one-or-many values suffices. Only STRING values under a
                 ;; URL-typed key are flagged here; a map value (e.g. a nested
                 ;; Identity under :identity) yields no string here and is
                 ;; recursed into below -- a dangerous scheme nested under a
                 ;; NON-URL-typed key (e.g. {:url {:inner "js:.."}}) is
                 ;; intentionally not flagged, as CTIM URI fields are string-
                 ;; typed; the recursion only re-flags string values that again
                 ;; sit directly under a url-typed key.
                 ;; `tree-seq` over the sequential/set nesting scans a value under a
                 ;; url-typed key at ANY list depth: the earlier `(if (coll? v) v [v])`
                 ;; unwrapped exactly one level, so `{:url [["javascript:.."]]}` slipped
                 ;; through (the inner vector is a non-string, nil-guarded away, and the
                 ;; recursion below then descends into a value no longer under a
                 ;; url-typed key). Maps are deliberately NOT a branch here (only
                 ;; `sequential?`/`set?`), so a nested Identity map under a url-typed key
                 ;; is still handled only by the recursion, not flagged as a string here;
                 ;; sets stay in scope (a set-valued url field is flagged as before).
                 (for [item (tree-seq (some-fn sequential? set?) seq v)
                       :when (string? item)
                       :let [scheme (unsafe-url-scheme item)]
                       :when scheme]
                   {:field k :scheme scheme :value item}))
               (collect-unsafe-url-fields v)))
            x)
    (coll? x) (mapcat collect-unsafe-url-fields x)
    :else nil))
