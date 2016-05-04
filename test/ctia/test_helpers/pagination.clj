(ns ctia.test-helpers.pagination
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [core :as helpers :refer [get]]
             [fake-whoami-service :as whoami-helpers]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn limit-test [route headers]
  (testing (str route " with a limit")
    (let [{full-status :status
           full-res :parsed-body
           full-headers :headers} (get route :headers headers)

          total (count full-res)
          limit (/ total 2)

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
  (testing (str route " with limit and offset")
    (let [offset 2
          {full-status :status
           full-res :parsed-body
           full-headers :headers} (get route :headers headers)

          total (count full-res)
          limit (- (/ total 2) offset)

          {limited-status :status
           limited-res :parsed-body
           limited-headers :headers} (get route
                                          :headers headers
                                          :query-params {:limit limit
                                                         :offset offset})]

      (is (= 200 full-status limited-status))
      (is (= limit (count limited-res)))
      (is (deep= (->> full-res
                      (drop offset)
                      (take limit)) limited-res))

      (is (= (clojure.core/get limited-headers "X-Total-Hits")
             (clojure.core/get full-headers "X-Total-Hits")))

      (is (deep=
           {:X-Total-Hits (str total)
            :X-Previous (str "limit=" limit "&offset=0")
            :X-Next (str "limit=" limit "&offset=" (+ offset limit))}

           (-> limited-headers
               clojure.walk/keywordize-keys
               (select-keys [:X-Total-Hits :X-Previous :X-Next])))))))

(defn sort-test [route headers sort-fields]
  (testing (str route " with sort")

    (let [results (map (fn [field]
                         (->> (get route
                                   :headers headers
                                   :query-params {:sort_by (name field)
                                                  :sort_order "desc"})
                              :parsed-body
                              (map field))) sort-fields)]

      (doall (map #(is (apply >= %)) results)))))

(defn edge-cases-test [route headers]
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
  (limit-test route headers)
  (offset-test route headers)
  (sort-test route headers sort-fields)
  (edge-cases-test route headers))
