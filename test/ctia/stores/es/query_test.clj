(ns ctia.stores.es.query-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ctia.entity.entities :as entities]
   [ctia.stores.es.query :as es.query]))

(defn- all-entities-es-mapping
  "Returns a map where each key is the entity identifier and value its ES mapping props"
  []
  (->> (entities/all-entities)
       (map (fn [[k v]]
              (hash-map
               (keyword k)
               (-> v :es-mapping (get (name k)) :properties))))
       (apply merge)))

(def ^:private expected-mapping-per-entity
  {:actor {"source" "source.text"}
   :asset {"source" "source.text"}
   :asset-mapping {"source" "source.text"}
   :asset-properties {"source" "source.text"}
   :attack-pattern {"source" "source.text"}
   :campaign {"source" "source.text"}
   :casebook {"source" "source.text"}
   :coa {"source" "source.text"}
   :data-table {"source" "source.text"}
   :event {}
   :feed {}
   :feedback {"source" "source.text"}
   :identity {}
   :identity-assertion {"source" "source.text"}
   :incident {"source" "source.text"}
   :indicator {"source" "source.text"}
   :investigation {"source" "source.text"}
   :judgement {"source" "source.text"}
   :malware {"source" "source.text"}
   :relationship {"source" "source.text"}
   :sighting {"source" "source.text"}
   :target-record {"source" "source.text"}
   :tool {"source" "source.text"}
   :vulnerability {"source" "source.text"}
   :weakness {"source" "source.text"}})

(deftest searchable-fields-map-test
  (testing "returns correct mapping for every entity"
    (is (= expected-mapping-per-entity
           (->> (all-entities-es-mapping)
                (map (fn [[k v]]
                       (hash-map k (es.query/searchable-fields-map v))))
                (apply merge)))))
  (testing "fake nested properties"
    (let [es-mappings (-> (all-entities-es-mapping)
                          (update-in
                           [:incident :description]
                           assoc :type "keyword"
                           :fields {:test-field {:type "text"}})
                          (update-in
                           [:vulnerability :configurations :properties :nodes :properties :operator]
                           assoc :type "keyword" :fields {:operator-text {:type "text"}}))
          digest-searchable-map (fn [mappings]
                                  (->> mappings
                                       (map (fn [[k v]]
                                              (hash-map k (es.query/searchable-fields-map v))))
                                       (apply merge)))
          expected-result {:incident {"description" "description.test-field"
                                      "source" "source.text"}
                           :vulnerability {"configurations.nodes.operator"
                                           "configurations.nodes.operator.operator-text"
                                           "source" "source.text"}}]
      (is (= expected-result
             (select-keys (digest-searchable-map es-mappings) [:incident :vulnerability])))
      (testing "nested sub-field added into a non-keyword field should be ignored"
        (let [es-mappings (-> es-mappings
                              (update-in
                               [:incident :title]
                               assoc :type "text" :fields {:ignored-and-sad {:type "text"}}))]
          (is (= expected-result
                 (select-keys (digest-searchable-map es-mappings) [:incident :vulnerability]))))))))
