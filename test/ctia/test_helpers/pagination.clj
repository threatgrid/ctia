(ns ctia.test-helpers.pagination
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [ctim.domain.id :as id]
            [ctia.test-helpers.core :as helpers :refer [get]]))

(defn total->limit
  "make a limit from a full list total and offset"
  [total offset]
  (-> (/ total 2)
      Math/ceil
      Math/round
      (- offset)))

(defn limit-test
  "test a list route with limit query param"
  [route headers]
  (headers (str route " with a limit")
           (let [{full-status :status
                  full-res :parsed-body
                  full-headers :headers} (get route :headers headers)

                 total (count full-res)
                 limit (total->limit total 0)

                 {limited-status :status
                  limited-res :parsed-body
                  limited-headers :headers} (get route
                                                 :query-params {:limit limit}
                                                 :headers headers)

                 limited-total (count limited-res)]

             (is (= 200 full-status limited-status))
             (is (= limit (int limited-total)))
             (is (= (take limit full-res)
                    limited-res))
             (is (= (clojure.core/get limited-headers "X-Total-Hits")
                    (clojure.core/get full-headers "X-Total-Hits"))))))


(defn x-next-test
  "Retrieving a full paginated response with X-Next"
  [route headers]
  (let [{full-status :status
         full-res :parsed-body}
        (get route
             :headers headers
             :query-params {:limit 10000})
        paginated-res
        (loop [results []
               x-next ""]
          (if x-next
            (let [{res :parsed-body
                   headers :headers}
                  (get (str route
                            (when (seq x-next) "&") x-next)
                       :headers headers)]
              (recur (concat results res)
                     (clojure.core/get headers "X-Next")))
            results))]

    (is (= 200 full-status))
    (is (=  (map :source full-res)
            (map :source paginated-res)))))

(defn offset-test
  "test a list route with offset and limit query params"
  [route headers]
  (testing (str route " with limit and offset")
    (let [offset 1
          {full-status :status
           full-res :parsed-body
           full-headers :headers}
          (get route
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
           limited-headers :headers} (get route
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

      (is (= (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))

      (is (= {:X-Total-Hits (str total)
              :X-Previous (str "limit=" limit "&offset=" (if (pos? prev-offset)
                                                           prev-offset 0))
              :X-Next expected-x-next}

             (-> limited-headers
                 clojure.walk/keywordize-keys
                 (select-keys [:X-Total-Hits :X-Previous :X-Next])))))))

(defn sort-field->sort-fn
  "Given a CTIA sort parameter, generate a sorting function to by used
   with sort-by, dealing with multi-sort as well as nested keys field sort"
  [sort-fields]
  (let [kws (map keyword
                 (str/split (name sort-fields) #","))
        nested-trans (map (fn [kw]
                            (if (str/includes? kw ".")
                              (apply comp
                                     (reverse
                                      (map keyword
                                           (str/split
                                            (name kw) #"\."))))
                              kw)) kws)]
    (apply juxt nested-trans)))

(defn sort-test
  "test a list route with all sort options"
  [route headers sort-fields]
  (testing (str route " with sort")
    (doseq [field sort-fields]
      (let [sort-fn (sort-field->sort-fn field)
            manually-sorted (->> (get route
                                      :headers headers
                                      :query-params {:limit 10000})
                                 :parsed-body
                                 (sort-by sort-fn))
            route-sorted (->> (get route
                                   :headers headers
                                   :query-params {:sort_by (name field)
                                                  :sort_order "asc"
                                                  :limit 10000})

                              :parsed-body)]
        (is (=
             manually-sorted
             route-sorted) field)))))

(defn edge-cases-test
  "miscellaneous test which may expose small caveats"
  [route headers]
  (testing (str route " with invalid offset")
    (let [total (-> (get route
                         :headers headers
                         :query-params {:limit 10000})
                    :parsed-body count)
          res (->> (get route
                        :headers headers
                        :query-params {:sort_by "id"
                                       :sort_order "asc"
                                       :limit 10
                                       :offset (+ total 1)})
                   :parsed-body)]
      (is (= [] res))))

  (testing (str route " with invalid limit")
    (let [total (-> (get route
                         :headers headers
                         :query-params {:limit 10000})
                    :parsed-body count)

          {status :status
           body :parsed-body} (get route
                                   :headers headers
                                   :query-params {:sort_by "id"
                                                  :sort_order "asc"
                                                  :limit (+ total 1)
                                                  :offset (+ total 1)})]
      (is (= 200 status))
      (is (= body [])))))

(defn pagination-test
  "all pagination related tests for a list route"
  [route headers sort-fields]
  (testing (str "pagination tests for: " route)
    (do (limit-test route headers)
        (offset-test route headers)
        (x-next-test route headers)
        (sort-test route headers sort-fields)
        (edge-cases-test route headers))))

(defn pagination-test-no-sort
  "all pagination related tests for a list route"
  [route headers]
  (testing (str "paginations tests for: " route)
    (do (limit-test route headers)
        (offset-test route headers))))
