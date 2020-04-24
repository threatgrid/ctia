(ns ctia.test-helpers.aggregate
  (:require [clj-http.client :as client]
            [clj-momo.lib.clj-time.core :as time]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.format :as tf]
            [ctia.test-helpers.core :as hc]
            [clojure.string :as string]
            [schema-generators.generators :as g]
            [ctia.http.routes.common :refer [now]]
            [schema-tools.core :as st]
            [ctia.test-helpers.fixtures :refer [n-examples]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.test :refer [deftest is testing]]))

(defn metric-raw
  [agg-type entity search-params agg-params]
  (let [metric-uri (format "ctia/%s/metric/%s"
                           (name entity)
                           (name agg-type))]
    (-> (hc/get metric-uri
                :accept :json
                :headers {"Authorization" "45c1f5e3f05d0"}
                :query-params (into search-params agg-params))
        :parsed-body
        keywordize-keys)))

(def cardinality (partial metric-raw :cardinality))
(def histogram (partial metric-raw :histogram))
(def topn (partial metric-raw :topn))

(defn parse-field
  [field]
  (map keyword
       (string/split (name field) #"\.")))

(defn flatten-list-values
  [values]
  (apply concat
         (map set values)))

(defn- get-values
  [examples field]
  (let [parsed (parse-field field)]
    (keep #(get-in % parsed) examples)))

(defn- normalized-values
  [examples field]
  (let [values (get-values examples field)
        flattened (cond-> values
                    (vector? (first values)) flatten-list-values)]
    (map string/lower-case flattened)))

(defn- check-from-to
  [from to]
  (is (<= (time/in-years
           (time/interval (tc/from-string from)
                          (tc/from-string to)))
          1)
      "[from to[ should not exceed one year"))

(defn- test-cardinality
  "test one field cardinality, examples are already created."
  [examples entity field]
  (testing (format "cardinality %s %s" entity field)
    (let [expected (-> (normalized-values examples field)
                       set
                       count)
          {:keys [from to filters]
           :as res} (cardinality entity
                                 {:query "*"
                                  :from "2020-01-01"}
                                 {:aggregate-on (name field)})]
      (is (= expected
             (get-in res (parse-field field))))
      (check-from-to from to))))

(defn- test-topn
  "test one field topn, examples are already created."
  [examples entity field limit]
  (testing (format "topn %s %s" entity field)
    (let [expected (->> (normalized-values examples field)
                        frequencies
                        (sort-by val)
                        reverse
                        (take limit)
                        vals)
          {:keys [from to]
           :as res} (topn entity
                          {:from "2020-01-01"}
                          {:aggregate-on (name field)
                           :limit limit})]
      (is (= expected
             (->> (parse-field field)
                  (get-in res)
                  (map :value))))
      (check-from-to from to))))

(defn- to-granularity-first-day
  [granularity date]
  (let [first-day (cond-> date
                    (= :month granularity) time/first-day-of-the-month)]
    (time/date-time (time/year first-day)
                    (time/month first-day)
                    (time/day first-day))))

(defn- make-histogram-res
  [dates]
  (->> (frequencies dates)
       (sort-by key)
       (map (fn [[k v]]
              {:key (str k) :value v}))))

(defn- test-histogram
  "test one field histogram, examples are already created"
  [examples entity field granularity]
  (testing (format "histogram %s %s" entity field)
    (let [parsed (parse-field field)
          values (keep #(get-in % parsed) examples)
          from-str "2020-03-01T00:00:00.000Z"
          to-str "2020-10-01T00:00:00.000Z"
          from (tf/parse from-str)
          to (tf/parse to-str)
          date-values (filter #(and (time/within? from to %)
                                    (time/before? % to))
                              (map tf/parse values))
          res-days (map #(to-granularity-first-day granularity %)
                        date-values)
          expected (make-histogram-res res-days)
          {:keys [from to] :as res} (histogram entity
                                               {:from from-str
                                                :to to-str}
                                               {:aggregate-on (name field)
                                                :granularity (name granularity)})]
      (is (= expected
             (->> (get-in res parsed)
                  (filter #(pos? (:value %))))))
      (check-from-to from to))))

(defn schema-enumerable-fields
  [schema fields]
  (->> (st/select-keys schema fields)
       st/required-keys))

(defn generate-date
  []
  (format "2020-%02d-%02dT%02d:00:00.000Z"
          (inc (rand-int 11))
          (inc (rand-int 28))
          (rand-int 24)))

(defn append-date-field
  [doc field]
  (let [prepared (parse-field field)]
    (assoc-in doc prepared (generate-date))))

(defn generate-date-fields
  [fields]
  (reduce append-date-field
          {}
          fields))

(defn generate-n-entity
  [{:keys [schema
           entity-minimal
           enumerable-fields
           date-fields]}
   n]
  (let [enumerable-schema (schema-enumerable-fields schema enumerable-fields)
        base-doc (dissoc entity-minimal :id)]
    (doall
     (repeatedly n (fn [] (merge base-doc
                                 (g/generate enumerable-schema)
                                 (generate-date-fields date-fields)))))))

(defn test-metric-routes
  [{:keys [entity
           plural
           enumerable-fields
           date-fields] :as metric-params}]
  (let [docs (generate-n-entity metric-params 10)]
    (with-redefs [;; ensure from coercion in proper one year range
                  now (-> (tc/from-string "2020-12-31")
                          tc/to-date
                          constantly)]
      (hc/post-bulk {plural docs})
      (doseq [field enumerable-fields]
        (test-cardinality docs entity field)
        (test-topn docs entity field 3))
      (doseq [field date-fields]
        (test-histogram docs entity field :day)
        (test-histogram docs entity field :month)))))
