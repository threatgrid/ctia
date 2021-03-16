(ns ctia.test-helpers.pagination
  (:require [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [clojure.walk :as walk]
            [ctim.domain.id :as id]
            [ctia.test-helpers.aggregate :as aggregate]
            [ctia.test-helpers.core :as helpers :refer [GET]]))

(defn total->limit
  "make a limit from a full list total and offset"
  [total offset]
  {:post [(pos? %)]}
  (-> (/ total 2)
      Math/ceil
      Math/round
      (- offset)))

(defn limit-test
  "test a list route with limit query param"
  [app route headers]
  (headers (str route " with a limit")
           (let [{full-status :status
                  full-res :parsed-body
                  full-headers :headers} (GET app route :headers headers)

                 total (count full-res)
                 limit (total->limit total 0)

                 {limited-status :status
                  limited-res :parsed-body
                  limited-headers :headers} (GET app
                                                 route
                                                 :query-params {:limit limit}
                                                 :headers headers)

                 limited-total (count limited-res)]

             (is (= 200 full-status limited-status))
             (is (= limit (int limited-total)))
             (is (= (take limit full-res)
                    limited-res))
             (is (= (get limited-headers "X-Total-Hits")
                    (get full-headers "X-Total-Hits"))))))


(defn x-next-test
  "Retrieving a full paginated response with X-Next"
  [app route headers]
  (let [{full-status :status
         full-res :parsed-body}
        (GET app
             route
             :headers headers
             :query-params {:limit 10000})
        paginated-res
        (loop [results []
               x-next ""]
          (if x-next
            (let [{res :parsed-body
                   headers :headers}
                  (GET app
                       (str route
                            (when (seq x-next) "&") x-next)
                       :headers headers)]
              (recur (into results res)
                     (get headers "X-Next")))
            results))]

    (is (= 200 full-status))
    (is (=  (map :source full-res)
            (map :source paginated-res)))))

(defn offset-test
  "test a list route with offset and limit query params"
  [app route headers]
  (testing (str route " with limit and offset")
    (let [offset 1
          {full-status :status
           full-res :parsed-body
           full-headers :headers}
          (GET app
               route
               :headers headers
               :query-params {:limit 10000
                              :sort_by "id"})

          total (count full-res)
          limit (total->limit total offset)
          last-record (nth full-res (- (+ limit offset) 1))
          search-after-id (when-let [last-id (:id last-record)]
                            (id/short-id
                              (id/long-id->id last-id)))
          base-x-next (format "limit=%s&offset=%s"
                              limit
                              (+ offset limit)
                              search-after-id)
          expected-x-next (if search-after-id
                            (str base-x-next "&search_after=" search-after-id)
                            base-x-next)
          {limited-status :status
           limited-res :parsed-body
           limited-headers :headers} (GET app
                                          route
                                          :headers headers
                                          :query-params {:limit limit
                                                         :offset offset
                                                         :sort_by "id"})
          prev-offset (- offset limit)]

      (is (= 200 full-status limited-status))
      (is (= limit (count limited-res)))
      (is (= (->> full-res
                  (drop offset)
                  (take limit)) limited-res))

      (is (= (get limited-headers "X-Total-Hits")
             (get full-headers "X-Total-Hits")))

      (is (= {:X-Total-Hits (str total)
              :X-Previous (str "limit=" limit "&offset=" (if (pos? prev-offset)
                                                           prev-offset 0))
              :X-Next expected-x-next}

             (-> limited-headers
                 walk/keywordize-keys
                 (select-keys [:X-Total-Hits :X-Previous :X-Next])))))))

(defn- sort-by-fn
  "Auxiliary function for sorting nested collections."
  {:example (comment
              (let [coll [{:foo [{:bar {:zop 5}}]}
                          {:foo [{:bar {:zop 1}}]}]]
                (sort-by #(sort-by-fn % :foo.bar.zop) coll))
                 ;; => ({:foo [{:bar {:zop 1}}]}
                 ;;     {:foo [{:bar {:zop 5}}]})
              )}
  [m field]
  (let [v (-> m (aggregate/es-get-in (aggregate/parse-field field)))]
    (if (sequential? v)
      (first v)
      v)))

(defn- grab-vals [field coll]
  (map
   #(aggregate/es-get-in % ( aggregate/parse-field field))
   coll))

(defn sort-test
  "test a list route with all sort options"
  [app route headers sort-fields]
  (testing (str route " with sort")
    (doseq [field sort-fields]
      (let [manually-sorted (->> (GET app
                                      route
                                      :headers headers
                                      :query-params {:limit 10000})
                                 :parsed-body
                                 (sort-by #(sort-by-fn % field)))
            route-sorted (->> (GET app
                                   route
                                   :headers headers
                                   :query-params {:sort_by    (name field)
                                                  :sort_order "asc"
                                                  :limit      10000})
                              :parsed-body)]
        (is (->> [manually-sorted route-sorted]
                 ;; no need to compare collections with nested maps included,
                 ;; since we only care about the order
                 (map (partial grab-vals field))
                 (apply =))
            (str "sort-test for field: " field))))))

(defn edge-cases-test
  "miscellaneous test which may expose small caveats"
  [app route headers]
  (testing (str route " with invalid offset")
    (let [total (-> (GET app
                         route
                         :headers headers
                         :query-params {:limit 10000})
                    :parsed-body count)
          res (->> (GET app
                        route
                        :headers headers
                        :query-params {:sort_by "id"
                                       :sort_order "asc"
                                       :limit 10
                                       :offset (+ total 1)})
                   :parsed-body)]
      (is (= [] res))))

  (testing (str route " with invalid limit")
    (let [total (-> (GET app
                         route
                         :headers headers
                         :query-params {:limit 10000})
                    :parsed-body count)

          {status :status
           body :parsed-body} (GET app
                                   route
                                   :headers headers
                                   :query-params {:sort_by "id"
                                                  :sort_order "asc"
                                                  :limit (+ total 1)
                                                  :offset (+ total 1)})]
      (is (= 200 status))
      (is (= body [])))))

(defn pagination-test
  "all pagination related tests for a list route"
  [app route headers sort-fields]
  (testing (str "pagination tests for: " route)
    (do (limit-test app route headers)
        (offset-test app route headers)
        (x-next-test app route headers)
        (sort-test app route headers sort-fields)
        (edge-cases-test app route headers))))

(defn pagination-test-no-sort
  "all pagination related tests for a list route"
  [app route headers]
  (testing (str "paginations tests for: " route)
    (do (limit-test app route headers)
        (offset-test app route headers))))
