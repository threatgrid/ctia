(ns ctia.test-helpers.pagination
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.test-helpers [core :as helpers :refer [get]]]))

(defn total->limit [total offset]
  "make a limit from a full list total and offset"
  (-> (/ total 2)
      Math/ceil
      Math/round
      (- offset)))

(defn limit-test [route headers]
  "test a list route with limit query param"
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
             (is (deep= (take limit full-res)
                        limited-res))
             (is (= (clojure.core/get limited-headers "X-Total-Hits")
                    (clojure.core/get full-headers "X-Total-Hits"))))))

(defn offset-test [route headers]
  "test a list route with offset and limit query params"

  (testing (str route " with limit and offset")
    (let [offset 1
          {full-status :status
           full-res :parsed-body
           full-headers :headers} (get route :headers headers)

          total (count full-res)
          limit (total->limit total offset)

          {limited-status :status
           limited-res :parsed-body
           limited-headers :headers} (get route
                                          :headers headers
                                          :query-params {:limit limit
                                                         :offset offset})
          prev-offset (- offset limit)]

      (is (= 200 full-status limited-status))
      (is (= limit (count limited-res)))
      (is (deep= (->> full-res
                      (drop offset)
                      (take limit)) limited-res))

      (is (= (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))

      (is (deep=
           {:X-Total-Hits (str total)
            :X-Previous (str "limit=" limit "&offset=" (if (pos? prev-offset)
                                                         prev-offset 0))
            :X-Next (str "limit=" limit "&offset=" (+ offset limit))}

           (-> limited-headers
               clojure.walk/keywordize-keys
               (select-keys [:X-Total-Hits :X-Previous :X-Next])))))))

(defn sort-test [route headers sort-fields]
  "test a list route with all sort options"

  (testing (str route " with sort")
    (doall (map (fn [field]
                  (is (deep=
                       (->> (get route :headers headers)
                            :parsed-body
                            (sort-by field)
                            (map field)
                            (remove nil?))
                       (->> (get route
                                 :headers headers
                                 :query-params {:sort_by (name field)
                                                :sort_order "asc"})
                            :parsed-body
                            (map field)
                            (remove nil?)))))
                sort-fields))))

(defn edge-cases-test [route headers]
  "miscellaneous test which may expose small caveats"

  (testing (str route " with invalid offset")
    (let [total (-> (get route :headers headers) :parsed-body count)
          res (->> (get route
                        :headers headers
                        :query-params {:sort_by "id"
                                       :sort_order "asc"
                                       :limit 10
                                       :offset (+ total 1)})
                   :parsed-body)]
      (is (deep= [] res))))


  (testing (str route " with invalid limit")
    (let [total (-> (get route :headers headers) :parsed-body count)

          {status :status
           body :parsed-body} (get route
                                   :headers headers
                                   :query-params {:sort_by "id"
                                                  :sort_order "asc"
                                                  :limit (+ total 1)
                                                  :offset (+ total 1)})]
      (is (= 200 status))
      (is (deep= body [])))))

(defn pagination-test [route headers sort-fields]
  "all pagination related tests for a list route"
  (testing (str "pagination tests for: " route)
    (do (limit-test route headers)
        (offset-test route headers)
        (sort-test route headers sort-fields)
        (edge-cases-test route headers))))

(defn pagination-test-no-sort [route headers sort-fields]
  "all pagination related tests for a list route"
  (testing (str "paginations tests for: " route)
    (do (limit-test route headers)
        (offset-test route headers))))
