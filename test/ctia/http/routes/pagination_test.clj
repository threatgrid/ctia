(ns ctia.http.routes.pagination-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [schema-generators.generators :as g]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.schemas.judgement :refer [NewJudgement]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-observable-routes-paging
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "with 30 judgements test setup"
    (let [doc-count 30
          headers {"api_key" "45c1f5e3f05d0"}
          new-judgements (->> (g/sample doc-count NewJudgement)
                              (map #(merge % {:observable {:type "ip"
                                                           :value "1.2.3.4"}
                                              :disposition 5
                                              :disposition_name "Unknown"})))
          judgement-post-responses (map #(post "ctia/judgement"
                                               :body %
                                               :headers headers) new-judgements)
          stored-judgements (->> judgement-post-responses
                                 (map :body))]

      (is (= doc-count (count stored-judgements)))))


  (testing "Judgements by observabe with a limit"
    (let [limit 10
          {limited-status :status
           limited-judgements :parsed-body
           limited-headers :headers}
          (get "ctia/ip/1.2.3.4/judgements"
               :query-params {:limit limit}
               :headers {"api_key" "45c1f5e3f05d0"})

          {full-status :status
           full-judgements :parsed-body
           full-headers :headers}

          (get "ctia/ip/1.2.3.4/judgements"
               :headers {"api_key" "45c1f5e3f05d0"})]

      (is (= 200 full-status))
      (is (= 200 limited-status))
      (is (= limit (count limited-judgements)))
      (is (deep= (take limit full-judgements)
                 limited-judgements))
      (is (= (str "30")
             (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))))


  (testing "Judgements by observable with limit and offset"
    (let [limit 10
          offset 10
          {limited-status :status
           limited-judgements :parsed-body
           limited-headers :headers}

          (get "ctia/ip/1.2.3.4/judgements"
               :query-params {:limit limit
                              :offset offset}
               :headers {"api_key" "45c1f5e3f05d0"})

          {full-status :status
           full-judgements :parsed-body
           full-headers :headers}

          (get "ctia/ip/1.2.3.4/judgements"
               :headers {"api_key" "45c1f5e3f05d0"})]

      (is (= 200 full-status))
      (is (= 200 limited-status))
      (is (= limit (count limited-judgements)))
      (is (deep= (->> full-judgements
                      (drop offset)
                      (take limit))
                 limited-judgements))

      (is (= (str "30")
             (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))

      (is (deep=
           {:X-Total-Hits "30"
            :X-Previous "limit=10&offset=0"
            :X-Next "limit=10&offset=20"}

           (-> limited-headers
               clojure.walk/keywordize-keys
               (select-keys [:X-Total-Hits :X-Previous :X-Next]))))))

  (testing "Judgements by observable with limit and sort"
    (let [priorities (->> (get "ctia/ip/1.2.3.4/judgements"
                               :headers {"api_key" "45c1f5e3f05d0"}
                               :query-params {:sort_by "priority"
                                              :sort_order "asc"
                                              :limit 10})
                          :parsed-body
                          (map :priority))
          severities (->> (get "ctia/ip/1.2.3.4/judgements"
                               :headers {"api_key" "45c1f5e3f05d0"}
                               :query-params {:sort_by "severity"
                                              :sort_order "desc"
                                              :limit 10})
                          :parsed-body
                          (map :severity))]
      (is (apply <= priorities))
      (is (apply >= severities))))

  (testing "Judgements by observable with invalid offset"
    (let [res (->> (get "ctia/ip/1.2.3.4/judgements"
                        :headers {"api_key" "45c1f5e3f05d0"}
                        :query-params {:sort_by "priority"
                                       :sort_order "asc"
                                       :limit 10
                                       :offset 31})
                   :parsed-body)]
      (is (deep= [] res))))


  (testing "Judgements by observable with huge limit"
    (let [status (->> (get "ctia/ip/1.2.3.4/judgements"
                           :headers {"api_key" "45c1f5e3f05d0"}
                           :query-params {:sort_by "priority"
                                          :sort_order "asc"
                                          :limit 100000000
                                          :offset 31})
                      :status)]
      (is (= 200 status)))))
