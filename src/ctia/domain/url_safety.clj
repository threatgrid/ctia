(ns ctia.domain.url-safety
  "Pure predicates backing the ingest URL-scheme gate (XFV-135): reject a
   URL-typed field carrying a scheme other than http(s), as defense-in-depth
   against stored XSS. Design notes (threat model, entity/normalization details,
   the diff tradeoff): resources/ctia/public/doc/url-scheme-validation.md."
  (:require
   [clojure.string :as str]
   [ctia.schemas.core :as schemas])
  (:import
   java.util.Locale))

(def safe-url-schemes
  "Allowlist of URL schemes permitted in URL-typed fields on ingest; any other
   scheme is rejected. See doc/url-scheme-validation.md."
  #{"http" "https"})

(def url-typed-keys
  "CTIM keys whose values are URI-typed and rendered as hyperlinks downstream, so
   are scheme-checked. Hand-curated across CTIM schemas; `:identity` is URI-typed
   only in RelatedIdentity (a map elsewhere, recursed into). Rationale and the
   assumptions this set makes: doc/url-scheme-validation.md."
  #{:url :source_uri :origin_uri :reason_uri :identity})

;; RFC 3986 scheme: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) followed by ":"
(def url-scheme-re #"(?i)^([a-z][a-z0-9+.\-]*):")

(defn code-point->str
  "Unicode code point -> String, or nil when out of range."
  [n]
  (when (<= 0 n 0x10FFFF)
    (String. (Character/toChars n))))

(def scheme-relevant-named-entities
  "The HTML named references that can reconstruct a scheme, mapped to their
   character: `&amp;`->`&` (the delimiter, enables double-encoding), `&colon;`->`:`
   (scheme delimiter), `&Tab;`/`&NewLine;`->whitespace the URL parser strips. This
   closed set is all the gate decodes; why only these: doc/url-scheme-validation.md."
  {"amp" "&" "colon" ":" "tab" "\t" "newline" "\n"})

(defn decode-html-entities
  "Decodes the numeric/hex/named HTML references (see `scheme-relevant-named-entities`)
   an attacker could use to mask a scheme, iterated to a fixed point so a reference
   revealed by an outer one is also decoded. Bounded by a budget: a value with more
   stacked layers is left partially decoded, so no dangerous scheme is presented.
   Details, incl. why decoding cannot cause a false positive: doc/url-scheme-validation.md."
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
                               ;; Locale/ROOT (not the JVM default) so a tr/az
                               ;; locale does not fold "NEWLINE" to a dotless-i and
                               ;; miss the map; the `or` nil-guards a miss.
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
  "When `v` is a string carrying a scheme not in `safe-url-schemes`, returns that
   (lower-cased, via Locale/ROOT) scheme; otherwise nil. Decodes HTML entities and
   strips characters a browser ignores when resolving a URL (ASCII controls/space
   \\x00-\\x20/\\x7f and Unicode format \\p{Cf} / separator \\p{Z} chars) so a split
   or masked scheme is unmasked first. `transient:` refs are exempt (CTIA-internal,
   non-executable). Details: doc/url-scheme-validation.md."
  [v]
  (when (and (string? v)
             (not (schemas/transient-id? v)))
    (let [normalized (-> v
                         decode-html-entities
                         (str/replace #"[\x00-\x20\x7f\p{Cf}\p{Z}]" ""))]
      (when-let [raw (some-> (re-find url-scheme-re normalized) second)]
        (let [scheme (.toLowerCase ^String raw Locale/ROOT)]
          (when-not (contains? safe-url-schemes scheme)
            scheme))))))

(defn collect-unsafe-url-fields
  "Recursively walks `x` (maps/vectors/sets) and returns a seq of
   {:field <key> :scheme <scheme> :value <string>} for every string value under a
   `url-typed-keys` key (at any list depth) carrying a disallowed scheme. Non-string
   values (e.g. a nested Identity map) are recursed into, not flagged. `:value` is
   the exact offending string, so the caller can diff it against prev-entity."
  [x]
  (cond
    (map? x)
    (mapcat (fn [[k v]]
              (concat
               (when (contains? url-typed-keys k)
                 ;; tree-seq over sequential/set nesting (NOT maps) scans a string
                 ;; value under a url-typed key at any list depth; a nested map is
                 ;; left to the recursion below. See doc/url-scheme-validation.md.
                 (for [item (tree-seq (some-fn sequential? set?) seq v)
                       :when (string? item)
                       :let [scheme (unsafe-url-scheme item)]
                       :when scheme]
                   {:field k :scheme scheme :value item}))
               (collect-unsafe-url-fields v)))
            x)
    (coll? x) (mapcat collect-unsafe-url-fields x)
    :else nil))
