(ns ctia.schemas.graphql.pagination-test
  (:require [ctia.schemas.graphql.pagination :as sut]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [schema.test :as st]
            [ctia.lib.pagination :as pag]
            [clojure.tools.logging :as log]))

(use-fixtures :once st/validate-schemas)

(deftest validate-paging-test
  (testing "Invalid pagination"
    (testing "when pagination is forwards and backwards"
      (is (thrown? Exception
                   #"cannot be forwards AND backwards"
                   (sut/validate-paging {:first 1
                                         :last 1
                                         :before "MQ=="}))))
    (testing "when paging limit is negative"
      (is (thrown? Exception
                   #"must be positive"
                   (sut/validate-paging {:first -1})))
      (is (thrown? Exception
                   #"must be positive"
                   (sut/validate-paging {:last -1
                                         :before "MQ=="}))))
    (testing "when the before argument is missing for backwards paging"
      (is (thrown? Exception
                   #"'before' argument is required"
                   (sut/validate-paging {:last -1})))))
  (testing "Paging direction"
    (testing "forwards"
      (is (= {:forward-paging? true
              :backward-paging? false}
             (sut/validate-paging {:first 1})))
      (is (= {:forward-paging? true
              :backward-paging? false}
             (sut/validate-paging {:first 1
                                   :after "MQ=="}))))
    (testing "backwards"
      (is (= {:forward-paging? false
              :backward-paging? true}
             (sut/validate-paging {:last 1
                                   :before "MQ=="}))))))

(deftest connection-params->paging-params-test
  (testing "Forward paging"
    (is (= {:forward-paging? true
            :backward-paging? false
            :limit 5
            :offset 0}
           (sut/connection-params->paging-params
            {:first 5})))
    (is (= {:forward-paging? true
            :backward-paging? false
            :limit 5
            :offset 5}
           (sut/connection-params->paging-params
            {:first 5
             :after (sut/serialize-cursor 4)})))
    (is (= {:forward-paging? true
            :backward-paging? false
            :limit 50
            :offset 0}
           (sut/connection-params->paging-params {}))))
  (testing "Backwards paging"
    (is (= {:forward-paging? false
            :backward-paging? true
            :limit 5
            :offset 5}
           (sut/connection-params->paging-params
            {:last 5
             :before (sut/serialize-cursor 10)})))
    (is (= {:forward-paging? false
            :backward-paging? true
            :limit 3
            :offset 0}
           (sut/connection-params->paging-params
            {:last 5
             :before (sut/serialize-cursor 3)})))))

(deftest data->egdges-test
  (is (= [{:cursor (sut/serialize-cursor 0)
           :node 0}
          {:cursor (sut/serialize-cursor 1)
           :node 1}]
         (sut/data->edges [0 1] 0))))

(defn gen-result
  [{:keys [offset limit]} hits]
  (let [data (range offset (+ offset limit))]
    (pag/response data offset limit hits)))

(deftest result->connection-response
  (testing "Forwards paging"
    (let [first-page-params (sut/connection-params->paging-params
                             {:first 2})
          first-page-response (sut/result->connection-response
                               (gen-result first-page-params 10)
                               first-page-params)
          next-page-params (sut/connection-params->paging-params
                            {:first 2
                             :after (get-in first-page-response
                                            [:pageInfo :endCursor])})
          next-page-response (sut/result->connection-response
                              (gen-result next-page-params 10)
                              next-page-params)]
      (is (= {:pageInfo
              {:hasNextPage true
               :hasPreviousPage false
               :startCursor "MA=="
               :endCursor "MQ=="}
              :totalCount 10
              :edges [{:cursor "MA=="
                       :node 0}
                      {:cursor "MQ=="
                       :node 1}]
              :nodes [0 1]}
             first-page-response))
      (is (= {:pageInfo
              {:hasNextPage true
               :hasPreviousPage true
               :startCursor "Mg=="
               :endCursor "Mw=="}
              :totalCount 10
              :edges [{:cursor "Mg=="
                       :node 2}
                      {:cursor "Mw=="
                       :node 3}]
              :nodes [2 3]}
             next-page-response))))
  (testing "Backwards paging"
    (let [last-page-params (sut/connection-params->paging-params
                            {:last 2
                             :before (sut/serialize-cursor 10)})
          last-page-response (sut/result->connection-response
                              (gen-result last-page-params 10)
                              last-page-params)
          next-page-params (sut/connection-params->paging-params
                            {:last 2
                             :before (get-in last-page-response
                                             [:pageInfo :startCursor])})
          next-page-response (sut/result->connection-response
                              (gen-result next-page-params 10)
                              next-page-params)]
      (is (= {:pageInfo
              {:hasNextPage true
               :hasPreviousPage false
               :startCursor "OA=="
               :endCursor "OQ=="}
              :totalCount 10
              :edges [{:cursor "OA=="
                       :node 8}
                      {:cursor "OQ=="
                       :node 9}]
              :nodes [8 9]}
             last-page-response))
      (is (= {:pageInfo
              {:hasNextPage true
               :hasPreviousPage true
               :startCursor "Ng=="
               :endCursor "Nw=="}
              :totalCount 10
              :edges [{:cursor "Ng=="
                       :node 6}
                      {:cursor "Nw=="
                       :node 7}]
              :nodes [6 7]}
             next-page-response)))))
