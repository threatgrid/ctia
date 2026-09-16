(ns ctia.entity.judgement.es-store-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.auth :as auth]
            [ctia.entity.judgement.es-store :as sut]
            [ductile.document :as ductile]
            [schema.test :refer [validate-schemas]]))

;; Turn on schema validation so every ident below is checked against
;; `auth/IdentityMap`, the shape `orgless-ident?` requires.
(use-fixtures :once validate-schemas)

(deftest list-active-by-observable-test
  ;; The verdict query is constrained to judgements owned by the caller's own
  ;; org (`groups`), and an org-less caller gets no verdict. ES-free checks on
  ;; the composed query.
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
      ;; The owner filter is present and lower-cased, as one element of the
      ;; top-level :filter vector; a revert of it is caught here.
      (is (some #{{:terms {"groups" ["victim-org"]}}}
                (get-in @captured [:bool :filter]))
          "the verdict is scoped to the caller's own org, regardless of authorized_* grants")
      ;; The pre-existing observable/time and access-control clauses must still
      ;; be composed in; pin the concrete term and restriction shape, not just
      ;; that some value is present.
      (is (some #{{:term {"observable.value" "203.0.113.213"}}}
                (get-in @captured [:bool :must]))
          "the concrete observable/time query is still applied")
      ;; Pin the concrete authorized_groups should-clause and the
      ;; minimum_should_match threshold, so a degenerate restriction (empty
      ;; :should or dropped `:minimum_should_match 1`) fails here.
      (let [restriction (first (filter #(get-in % [:bool :minimum_should_match])
                                       (get-in @captured [:bool :filter])))]
        (is (some #{{:terms {"authorized_groups" ["victim-org"]}}}
                  (get-in restriction [:bool :should]))
            "the base access-control restriction is still composed in")
        (is (= 1 (get-in restriction [:bool :minimum_should_match]))
            "the restriction still requires at least one should-clause to match"))))
  (testing "a multi-org caller scopes the verdict to ALL of its groups (lower-cased)"
    ;; A caller can belong to several orgs; the owner filter maps over every
    ;; group, so exercise it with more than one.
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
