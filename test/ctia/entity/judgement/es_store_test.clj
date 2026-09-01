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
      (is (= {:terms {"groups" ["victim-org"]}}
             (get-in @captured [:bool :filter]))
          "the verdict is scoped to the caller's own org, regardless of authorized_* grants")
      (is (some? (get-in @captured [:bool :must]))
          "the observable/time query is still applied")
      (is (some? (get-in @captured [:bool :should]))
          "the base access-control restriction is still applied"))))
