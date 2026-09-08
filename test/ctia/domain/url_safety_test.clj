(ns ctia.domain.url-safety-test
  "Unit tests for the pure URL-scheme predicates (XFV-135). These live in
   ctia.domain.url-safety so the security logic is testable directly, without
   re-implementing private helpers or routing every case through the flow's
   `url-scheme-check` wrapper (whose flow-level integration is covered in
   ctia.flows.crud-test)."
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.domain.url-safety :as sut]))

(deftest unsafe-url-scheme-basic-test
  (testing "flags a disallowed scheme, returning it lower-cased"
    (is (= "javascript" (sut/unsafe-url-scheme "javascript:alert(1)")))
    (is (= "data" (sut/unsafe-url-scheme "data:text/html;base64,PHN=")))
    (is (= "vbscript" (sut/unsafe-url-scheme "vbscript:msgbox(1)")))
    ;; LOW4: scheme lower-cased with Locale/ROOT, so an uppercase disallowed
    ;; scheme is still recognized as disallowed regardless of the JVM locale.
    (is (= "javascript" (sut/unsafe-url-scheme "JAVASCRIPT:alert(1)"))))
  (testing "allows http/https and scheme-less values (nil)"
    (is (nil? (sut/unsafe-url-scheme "https://example.com/a")))
    (is (nil? (sut/unsafe-url-scheme "HTTPS://example.com/a")))
    (is (nil? (sut/unsafe-url-scheme "http://example.org")))
    (is (nil? (sut/unsafe-url-scheme "/relative/path")))
    (is (nil? (sut/unsafe-url-scheme "example.com/a"))))
  (testing "nil-guards non-strings"
    (is (nil? (sut/unsafe-url-scheme 42)))
    (is (nil? (sut/unsafe-url-scheme nil)))
    (is (nil? (sut/unsafe-url-scheme {:a 1}))))
  (testing "exempts transient: references (CTIA-internal, non-executable)"
    (is (nil? (sut/unsafe-url-scheme "transient:0")))
    (is (nil? (sut/unsafe-url-scheme (str "transient:" (random-uuid)))))))

(deftest unsafe-url-scheme-amp-double-encoding-test
  ;; SEC1: `&amp;` is the canonical encoding of `&`, so `javascript&amp;colon;`
  ;; HTML-decodes to `javascript&colon;` then `javascript:` at the render sink.
  ;; Before adding "amp" to scheme-relevant-named-entities the named `&amp;` form
  ;; slipped through (nil => stored) while its numeric twin `&#38;` was caught --
  ;; exactly the double-encoding case the fixed-point loop exists to close.
  (testing "named-ampersand double-encoding is unmasked and flagged"
    (is (= "javascript" (sut/unsafe-url-scheme "javascript&amp;colon;alert(1)")))
    (is (= "javascript" (sut/unsafe-url-scheme "javascript&amp;#58;alert(1)"))))
  (testing "numeric-ampersand twin remains flagged (regression guard)"
    (is (= "javascript" (sut/unsafe-url-scheme "javascript&#38;colon;alert(1)"))))
  (testing "a legitimate https URL with an &amp; query separator is NOT flagged"
    ;; The scheme sits at the front and is extracted first, so decoding `&amp;`
    ;; in the query string cannot turn a safe URL dangerous (no false positive).
    (is (nil? (sut/unsafe-url-scheme "https://example.com/q?a=1&amp;b=2")))))

(deftest unsafe-url-scheme-locale-independent-test
  ;; LOW4: the extracted scheme is lower-cased with Locale/ROOT, not the JVM
  ;; default locale. Under a Turkish/Azeri default, str/lower-case folds the ASCII
  ;; "I" in "JAVASCRIPT" to the dotless "ı" ("javascrıpt"); Locale/ROOT keeps it
  ;; "javascript". Swapping line back to str/lower-case makes this assertion fail
  ;; (the returned scheme string differs), and keeps the two lower-casings
  ;; (decoder + extractor) consistent and locale-independent.
  (let [default (java.util.Locale/getDefault)]
    (try
      (java.util.Locale/setDefault (java.util.Locale/forLanguageTag "tr"))
      (is (= "javascript" (sut/unsafe-url-scheme "JAVASCRIPT:alert(1)")))
      (is (nil? (sut/unsafe-url-scheme "HTTPS://example.com")))
      (finally (java.util.Locale/setDefault default)))))

(deftest unsafe-url-scheme-control-and-ignorable-test
  (testing "strips ASCII controls incl. \\x7f (DEL) before scheme extraction"
    ;; LOW1: &#127; is DEL; the strip class covers \x00-\x20 AND \x7f. Removing
    ;; \x7f from the class would let "java<DEL>script:" pass with no failing test.
    (is (= "javascript" (sut/unsafe-url-scheme "java&#127;script:alert(1)"))))
  (testing "strips non-ASCII ignorables the decoder can emit"
    (doseq [v ["java&#8203;script:alert(1)"   ; U+200B ZERO WIDTH SPACE (Cf)
               "&#65279;javascript:alert(1)"  ; U+FEFF BOM (Cf)
               "&#160;javascript:alert(1)"    ; U+00A0 NO-BREAK SPACE (Zs)
               "java&#173;script:alert(1)"]]  ; U+00AD SOFT HYPHEN (Cf)
      (is (= "javascript" (sut/unsafe-url-scheme v)) (str "must unmask: " v)))))

(deftest decode-html-entities-fixed-point-test
  (testing "peels multiple stacked encoding layers to reveal the delimiter"
    ;; LOW3: each pass peels one `&#38;`->`&` layer; the budget bounds the peel
    ;; depth. A triple-stacked ampersand still converges well within the budget.
    (is (= "javascript:alert(1)"
           (sut/decode-html-entities "javascript&#38;#38;#38;colon;alert(1)")))
    (is (= "javascript"
           (sut/unsafe-url-scheme "javascript&#38;#38;#38;colon;alert(1)"))))
  (testing "leaves an unparseable / oversized numeric entity untouched (converges)"
    (is (= "&#999999999999999999999;oke"
           (sut/decode-html-entities "&#999999999999999999999;oke"))))
  (testing "a stack deeper than the budget is left partially-encoded and fails safe"
    ;; The budget is load-bearing: it caps the peel depth at 8 passes. A value with
    ;; 10 stacked `&#38;` before the `colon;` cannot fully collapse to `javascript:`
    ;; within the budget, so the residual `&` sit between the scheme letters and the
    ;; `:` -- no http(s)-or-dangerous scheme is presented, `unsafe-url-scheme`
    ;; returns nil (not flagged, no throw), and a browser HTML-decodes only one layer
    ;; per render so it never reaches `javascript:` in a single hop. A budget-shrink
    ;; mutation that let this decode fully would flip the nil to "javascript".
    (let [deep (str "javascript" (apply str (repeat 10 "&#38;")) "colon;alert(1)")]
      (is (nil? (sut/unsafe-url-scheme deep))))))

(deftest code-point->str-range-guard-test
  (testing "returns nil past the max Unicode code point rather than throwing"
    ;; 0x110000 parses as an int but Character/toChars would throw
    ;; IllegalArgumentException; the (<= 0 n 0x10FFFF) guard must return nil so an
    ;; oversized numeric entity is left untouched instead of 500-ing.
    (is (nil? (sut/code-point->str 0x110000)))
    (is (nil? (sut/code-point->str -1))))
  (testing "decodes an in-range code point"
    (is (= "j" (sut/code-point->str 106)))
    (is (= ":" (sut/code-point->str 58)))))

(deftest collect-unsafe-url-fields-test
  (testing "returns the field, scheme and exact offending value"
    (is (= [{:field :source_uri :scheme "javascript" :value "javascript:alert(1)"}]
           (sut/collect-unsafe-url-fields {:source_uri "javascript:alert(1)"}))))
  (testing "recurses into nested maps and collections under URL-typed keys"
    (is (= [{:field :url :scheme "data" :value "data:text/html,x"}]
           (sut/collect-unsafe-url-fields
            {:external_references [{:source_name "s" :url "data:text/html,x"}]}))))
  (testing "does not flag dangerous-looking values in non-URL-typed fields"
    (is (empty? (sut/collect-unsafe-url-fields
                 {:description "uses javascript:alert(1)"
                  :value "javascript:alert(1)"}))))
  (testing "flags a value nested more than one list level under a URL-typed key"
    ;; The prior `(if (coll? v) v [v])` unwrapped exactly one level, so a value
    ;; nested deeper (a vector-of-vectors under :url) was seen as a non-string,
    ;; nil-guarded away, then recursed into as a value no longer under a URL-typed
    ;; key -- and silently passed. Not reachable via the API today (schema coercion
    ;; rejects a nested vector in a URI-typed field), but the helper must still scan
    ;; the value at any list depth. Pins the tree-seq flattening.
    (is (= [{:field :url :scheme "javascript" :value "javascript:a(1)"}]
           (sut/collect-unsafe-url-fields {:url [["javascript:a(1)"]]})))
    ;; a set nested below the top level exercises the set? half of the branch
    ;; predicate at depth (symmetry with the vector-of-vector case above).
    (is (= [{:field :url :scheme "javascript" :value "javascript:a(1)"}]
           (sut/collect-unsafe-url-fields {:url [#{"javascript:a(1)"}]}))))
  (testing "still flags a single-level collection- and set-valued URL field"
    (is (= [{:field :url :scheme "javascript" :value "javascript:a(1)"}]
           (sut/collect-unsafe-url-fields {:url ["javascript:a(1)"]})))
    (is (= [{:field :url :scheme "javascript" :value "javascript:a(1)"}]
           (sut/collect-unsafe-url-fields {:url #{"javascript:a(1)"}}))))
  (testing "a nested map under a URL-typed key is recursed, not flagged as a string"
    ;; tree-seq treats only sequential?/set? as branches, so a map value under a
    ;; URL-typed key yields no string here; a dangerous scheme under a NON-URL-typed
    ;; key inside it stays unflagged (CTIM URI fields are string-typed).
    (is (empty? (sut/collect-unsafe-url-fields {:url {:inner "javascript:a(1)"}})))))
