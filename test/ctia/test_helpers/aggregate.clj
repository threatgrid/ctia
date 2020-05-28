(ns ctia.test-helpers.aggregate
  (:require [clj-momo.lib.clj-time.core :as time]
            [clj-momo.lib.clj-time.coerce :as tc]
            [clj-momo.lib.clj-time.format :as tf]
            [ctia.test-helpers.core :as hc]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]
            [schema-generators.generators :as g]
            [ctia.http.routes.common :refer [now]]
            [schema-tools.core :as st]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.test :refer [is testing]]
            [clojure.test.check.generators :as gen]
            [schema.core :as s]))

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
  (mapcat set values))

(defn es-get-in
  "like get-in, but match keys in maps embedded in collections.
   This function is intended to simulate how ES match nested fields like a.b.
   for instance (get-in {:a [{:b 2} {:b 3}]} [:a :b]) returns nil
   while (es-get-in {:a [{:b 2} {:b 3}]} [:a :b]) returns '(2 3)"
  {:test (fn []
           (is (= (es-get-in {:a [{:b 2} {:b 3}]} [:a :b])
                      '(2 3)))
           (is (= (es-get-in {:a [2 3]} [:a])
                      [2 3]))
           (is (= (es-get-in {:a {:b [2 3]}} [:a :b])
                      [2 3]))
           (is (= (es-get-in {:a [{:b 2} {:b 3}]} [:a :b])
                      '(2 3)))
           (is (= (es-get-in {:a {:b 2 :c 3}} [:a :b])
                      2))
           (is (= (es-get-in {:a {:d 2 :c 3}} [:a :b])
                      nil))
           (is (= (es-get-in {:a [{:d 2} {:b 3}]} [:a :b])
                      '(3))))}
  [m ks]
  (reduce (fn [acc k]
            (cond
              (map? acc) (get acc k)
              (coll? acc) (seq (keep #(get % k) acc))
              :else (reduced acc)))
          m
          ks))

(defn- get-values
  [examples field]
  (let [parsed (parse-field field)]
    (keep #(es-get-in % parsed) examples)))

(defn- normalized-values
  [examples field]
  (let [values (get-values examples field)]
    (cond-> values
      (coll? (first values)) flatten-list-values)))

(defn- check-from-to
  [from-str to-str]
  (let [from (tc/from-string from-str)
        to (tc/from-string to-str)]
    (testing "should be applied only on non empty date"
      (is (some? from))
      (is (some? to)))
    (is (<= (time/in-years
             (time/interval from to))
            1)
        "[from to[ should not exceed one year")))

(defn error-helper-msg
  [explaining-values]
  (str "values: \n"
       (with-out-str (pprint explaining-values))))

(defn- test-cardinality
  "test one field cardinality, examples are already created."
  [examples entity field]
  (testing (format "cardinality %s %s" entity field)
    (let [unique-values (set (normalized-values examples field))
          expected (count unique-values)
          _ (assert (pos? expected))
          {{:keys [from to]} :filters
           :as res} (cardinality entity
                                 {:query "*"
                                  :from "2020-01-01"}
                                 {:aggregate-on (name field)})]
      (is (= expected
             (get-in (:data res) (parse-field field)))
          (error-helper-msg unique-values))
      (check-from-to from to))))

(defn- test-topn
  "test one field topn, examples are already created."
  [examples entity field limit]
  (testing (format "topn %s %s" entity field)
    (let [prepared (->> (normalized-values examples field)
                        frequencies
                        (sort-by val)
                        reverse)
          expected (vals (take limit prepared))
          _ (assert (every? pos? expected))
          {{:keys [from to]} :filters
           :as res} (topn entity
                          {:from "2020-01-01"}
                          {:aggregate-on (name field)
                           :limit limit})]
      (is (= expected
             (->> (parse-field field)
                  (get-in (:data res))
                  (map :value)))
          (error-helper-msg prepared))
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
          _ (assert (every? #(:value %) expected))
          {{:keys [from to]} :filters
           :as res} (histogram entity
                               {:from from-str
                                :to to-str}
                               {:aggregate-on (name field)
                                :granularity (name granularity)})]
      (is (= expected
             (->> (get-in (:data res) parsed)
                  (filter #(pos? (:value %))))))
      (check-from-to from to))))

(defn schema-enumerable-fields
  [schema fields]
  (->> (map (comp keyword first parse-field) fields)
       (st/select-keys schema)
       st/required-keys))

(defn generate-date
  [k]
  (let [month (inc (case k
                     :start_time (rand-int 6)
                     :end_time (+ 6 (rand-int 6))
                     (rand-int 11)))]
    (format "2020-%02d-%02dT%02d:00:00.000Z"
            month
            (inc (rand-int 28))
            (rand-int 24))))

(defn append-date-field
  [doc field]
  (let [prepared (parse-field field)]
    (assoc-in doc prepared (generate-date (last prepared)))))

(defn generate-date-fields
  [fields]
  (reduce append-date-field
          {}
          fields))

(def string-generator
  (->> (gen/sample gen/string-alphanumeric 20)
       (map string/lower-case)
       gen/elements))

(defn generate-n-entity
  [{:keys [new-schema
           entity-minimal
           enumerable-fields
           date-fields]}
   n]
  (let [enumerable-schema (schema-enumerable-fields new-schema enumerable-fields)
        base-doc (dissoc entity-minimal :id)]
    (doall
     (repeatedly n (fn [] (merge base-doc
                                 (g/generate enumerable-schema
                                             {s/Str string-generator})
                                 (generate-date-fields date-fields)))))))

(defn test-metric-routes
  [{:keys [entity
           plural
           enumerable-fields
           date-fields] :as metric-params}]
  (let [docs (generate-n-entity metric-params 100)]
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
