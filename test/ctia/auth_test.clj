(ns ctia.auth-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.auth :as auth]
            [schema.test :refer [validate-schemas]]))

;; Validate every ident below against `auth/IdentityMap` (`orgless-ident?` is an
;; `s/defn` taking `IdentityMap`), so a non-conformant shape fails loudly here
;; rather than silently.
(use-fixtures :once validate-schemas)

(deftest orgless-ident?-test
  ;; XFV-20 (LOW): pin `orgless-ident?` directly. Until now it was exercised only
  ;; transitively via `list-active-by-observable`; the mixed case in particular
  ;; (a real org alongside the not-logged-in sentinel) was unpinned.
  (let [ident #(hash-map :client-id "c" :login "l" :groups %)]
    (testing "no groups at all is org-less (JWT missing org/id, static group unset)"
      (is (true? (auth/orgless-ident? (ident [])))))
    (testing "only the not-logged-in sentinel is org-less (readonly-for-anonymous)"
      (is (true? (auth/orgless-ident? (ident auth/not-logged-in-groups)))))
    (testing "a blank group string is treated as absent (JWT blank org/id, static blank group)"
      (is (true? (auth/orgless-ident? (ident [""]))))
      (is (true? (auth/orgless-ident? (ident ["" ""])))))
    (testing "a real org is NOT org-less"
      (is (false? (auth/orgless-ident? (ident ["real-org"])))))
    (testing "a real org alongside a blank string is NOT org-less"
      (is (false? (auth/orgless-ident? (ident ["" "real-org"])))))
    (testing "a real org mixed with the sentinel is NOT org-less"
      ;; The sentinel-set equality check must not match a superset -- a caller
      ;; that carries a real org in addition to the sentinel still has a tenant.
      (is (false? (auth/orgless-ident? (ident (conj (vec auth/not-logged-in-groups)
                                                    "real-org"))))))))
