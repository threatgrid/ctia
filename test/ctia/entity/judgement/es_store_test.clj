(ns ctia.entity.judgement.es-store-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.auth :as auth]
            [ctia.entity.judgement.es-store :as sut]
            [ductile.document :as ductile]
            [schema.test :refer [validate-schemas]]))

;; XFV-20: turn on schema validation so every ident below is checked against
;; `auth/IdentityMap` -- `list-active-by-observable` reaches `auth/orgless-ident?`
;; (an `s/defn` taking `IdentityMap`), so this also proves the guard is exercised
;; with the conformant shape its docstring warns about.
(use-fixtures :once validate-schemas)

(deftest list-active-by-observable-test
  ;; XFV-20: a verdict is a tenant-local trust decision, so the verdict query is
  ;; constrained to judgements owned by the caller's own org (`groups`) and an
  ;; org-less caller gets no verdict. ES-free checks on the composed query.
  (testing "list-active-by-observable adds a mandatory, lower-cased groups filter for the caller's org"
    (let [captured (atom nil)
          state {:conn :fake-conn :index "judgement-index"}
          ident {:client-id "victim-client" :login "victim" :groups ["VICTIM-ORG"]}]
      (with-redefs [ductile/search-docs
                    (fn [_conn _index query _sort _es-params]
                      (reset! captured query)
                      {:data []})]
        (sut/list-active-by-observable state
                                       {:type "ip" :value "203.0.113.213"}
                                       ident
                                       (constantly nil)
                                       {}))
      ;; Load-bearing assertion: the owner filter is present and lower-cased.
      ;; It is one element of the top-level :filter vector (alongside the opaque
      ;; access-control restriction), so a revert of the owner filter is
      ;; detected here.
      (is (some #{{:terms {"groups" ["victim-org"]}}}
                (get-in @captured [:bool :filter]))
          "the verdict is scoped to the caller's own org, regardless of authorized_* grants")
      ;; Non-regression assertions: the pre-existing observable/time and
      ;; access-control clauses must still be composed into the query. These are
      ;; discriminating -- they pin the concrete observable term and the base
      ;; restriction's shape, not merely that *some* value is present.
      (is (some #{{:term {"observable.value" "203.0.113.213"}}}
                (get-in @captured [:bool :must]))
          "the concrete observable/time query is still applied")
      ;; The base access-control restriction must still be composed in. Pin the
      ;; CONCRETE authorized_groups should-clause and the minimum_should_match
      ;; threshold rather than asserting merely that *some* :should is present: a
      ;; refactor that returned a degenerate restriction (empty :should, or a
      ;; dropped `:minimum_should_match 1`) would silently drop access control
      ;; and must fail here.
      (let [restriction (first (filter #(get-in % [:bool :minimum_should_match])
                                       (get-in @captured [:bool :filter])))]
        (is (some #{{:terms {"authorized_groups" ["victim-org"]}}}
                  (get-in restriction [:bool :should]))
            "the base access-control restriction is still composed in")
        (is (= 1 (get-in restriction [:bool :minimum_should_match]))
            "the restriction still requires at least one should-clause to match"))))
  (testing "a multi-org caller scopes the verdict to ALL of its groups (lower-cased)"
    ;; A caller can legitimately belong to several orgs (e.g. via
    ;; ctia.auth.threatgrid). The owner filter maps over every group, so the
    ;; verdict is scoped to the union of the caller's own orgs -- exercise the
    ;; `map` with more than one group.
    (let [captured (atom nil)
          state {:conn :fake-conn :index "judgement-index"}
          ident {:client-id "multi-client" :login "multi" :groups ["ORG-A" "Org-B"]}]
      (with-redefs [ductile/search-docs
                    (fn [_conn _index query _sort _es-params]
                      (reset! captured query)
                      {:data []})]
        (sut/list-active-by-observable state
                                       {:type "ip" :value "203.0.113.213"}
                                       ident
                                       (constantly nil)
                                       {}))
      (is (some #{{:terms {"groups" ["org-a" "org-b"]}}}
                (get-in @captured [:bool :filter]))
          "every one of the caller's groups is included, lower-cased")))
  ;; A caller with no org (static-auth with a blank group, a JWT missing org/id)
  ;; or one carrying only the `readonly-for-anonymous` sentinel must bail out --
  ;; return nil WITHOUT querying -- rather than issue a match-nothing filter.
  (testing "an identity with no real org yields no verdict and issues no query"
    (let [called? (atom false)
          state {:conn :fake-conn :index "judgement-index"}]
      (with-redefs [ductile/search-docs
                    (fn [& _] (reset! called? true) {:data []})]
        (doseq [ident [{:client-id "no-org-client" :login "no-org" :groups []}
                       {:client-id "anon-client" :login "anonymous" :groups auth/not-logged-in-groups}]]
          (is (nil? (sut/list-active-by-observable state
                                                   {:type "ip" :value "203.0.113.213"}
                                                   ident
                                                   (constantly nil)
                                                   {}))
              "no org means no tenant-local verdict")))
      (is (false? @called?)
          "the store must not be queried when the caller has no org"))))
