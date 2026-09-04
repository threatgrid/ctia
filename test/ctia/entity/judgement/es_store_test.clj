(ns ctia.entity.judgement.es-store-test
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.entity.judgement.es-store :as sut]
            [ductile.document :as ductile]))

(deftest list-active-by-observable-verdict-owner-filter-test
  ;; XFV-20: a verdict is a tenant-local trust decision. The verdict query must
  ;; be constrained to judgements owned by the caller's own org (`groups`), so a
  ;; foreign judgement that merely lists the caller's org in `authorized_groups`
  ;; (or `authorized_users`) cannot dominate/poison the verdict. This is an
  ;; ES-free check that the guard clause is composed into the query.
  (testing "list-active-by-observable adds a mandatory, lower-cased groups filter for the caller's org"
    (let [captured (atom nil)
          state {:conn :fake-conn :index "judgement-index"}
          ident {:login "victim" :groups ["VICTIM-ORG"]}]
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
      ;; It is conjoined into a vector so a future :filter from
      ;; find-restriction-query-part is preserved rather than overwritten.
      (is (= [{:terms {"groups" ["victim-org"]}}]
             (get-in @captured [:bool :filter]))
          "the verdict is scoped to the caller's own org, regardless of authorized_* grants")
      ;; Non-regression assertions: the pre-existing observable/time and
      ;; access-control clauses must still be composed into the query. These do
      ;; not, on their own, detect a revert of the owner filter -- the
      ;; assertion above does.
      (is (some? (get-in @captured [:bool :must]))
          "the observable/time query is still applied")
      (is (some? (get-in @captured [:bool :should]))
          "the base access-control restriction is still applied"))))

(deftest list-active-by-observable-empty-groups-test
  ;; XFV-20 (CR1): a caller with no org (static-auth with the group unset, or a
  ;; JWT missing org/id) has no tenant to scope the verdict to. The guard must
  ;; bail out explicitly -- return nil WITHOUT querying -- rather than issue a
  ;; match-nothing {:terms {"groups" []}} filter.
  (testing "an identity with no groups yields no verdict and issues no query"
    (let [called? (atom false)
          state {:conn :fake-conn :index "judgement-index"}]
      (with-redefs [ductile/search-docs
                    (fn [& _] (reset! called? true) {:data []})]
        (doseq [ident [{:login "no-org" :groups []}
                       {:login "no-org" :groups nil}]]
          (is (nil? (sut/list-active-by-observable state
                                                   {:type "ip" :value "203.0.113.213"}
                                                   ident
                                                   (constantly nil)
                                                   {}))
              "no org means no tenant-local verdict")))
      (is (false? @called?)
          "the store must not be queried when the caller has no org"))))
