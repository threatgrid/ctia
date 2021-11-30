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
  {:identity {}
   :feed {"id" "id.text"}
   :feedback {"source" "source.text" "id" "id.text" "entity_id" "entity_id.text"}
   :investigation {"source" "source.text"
                   "targets.observables.type" "targets.observables.type.text"
                   "targets.observables.value" "targets.observables.value.text"
                   "id" "id.text"}
   :asset-mapping {"observable.type" "observable.type.text"
                   "observable.value" "observable.value.text"
                   "source" "source.text"
                   "id" "id.text"}
   :data-table {"source" "source.text" "id" "id.text"}
   :tool {"source" "source.text"
          "id" "id.text"
          "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
          "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :relationship {"source" "source.text" "id" "id.text"}
   :vulnerability {"source" "source.text" "id" "id.text"}
   :judgement {"observable.type" "observable.type.text"
               "observable.value" "observable.value.text"
               "source" "source.text"
               "id" "id.text"}
   :target-record {"source" "source.text"
                   "targets.observables.type" "targets.observables.type.text"
                   "targets.observables.value" "targets.observables.value.text"
                   "id" "id.text"}
   :weakness {"source" "source.text" "id" "id.text"}
   :coa {"source" "source.text" "id" "id.text"}
   :attack-pattern {"source" "source.text"
                    "id" "id.text"
                    "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
                    "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :incident {"confidence" "confidence.text"
              "id" "id.text"
              "categories" "categories.text"
              "assignees" "assignees.text"
              "discovery_method" "discovery_method.text"
              "promotion_method" "promotion_method.text"
              "source" "source.text"
              "intended_effect" "intended_effect.text"
              "severity" "severity.text"}
   :event {}
   :indicator {"source" "source.text"
               "id" "id.text"
               "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
               "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :campaign {"source" "source.text" "id" "id.text"}
   :asset-properties {"value" "value.text" "source" "source.text" "id" "id.text"}
   :sighting {"observables.type" "observables.type.text"
              "id" "id.text"
              "relations.source.value" "relations.source.value.text"
              "observables.value" "observables.value.text"
              "sensor_coordinates.observables.value" "sensor_coordinates.observables.value.text"
              "relations.related.value" "relations.related.value.text"
              "targets.observables.type" "targets.observables.type.text"
              "source" "source.text"
              "sensor_coordinates.observables.type" "sensor_coordinates.observables.type.text"
              "targets.observables.value" "targets.observables.value.text"
              "relations.source.type" "relations.source.type.text"
              "relations.related.type" "relations.related.type.text"}
   :casebook {"observables.type" "observables.type.text"
              "observables.value" "observables.value.text"
              "source" "source.text"
              "id" "id.text"}
   :asset {"source" "source.text" "id" "id.text"}
   :identity-assertion {"identity.observables.type" "identity.observables.type.text"
                        "identity.observables.value" "identity.observables.value.text"
                        "source" "source.text"
                        "id" "id.text"}
   :malware {"labels" "labels.text"
             "x_mitre_aliases" "x_mitre_aliases.text"
             "source" "source.text"
             "id" "id.text"
             "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
             "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :actor {"source" "source.text" "id" "id.text"}})

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
          expected-result (-> expected-mapping-per-entity
                              (select-keys [:incident :vulnerability])
                              (update
                               :incident assoc "description" "description.test-field")
                              (update :vulnerability assoc
                                      "configurations.nodes.operator"
                                      "configurations.nodes.operator.operator-text"))]
      (is (= expected-result
             (select-keys (digest-searchable-map es-mappings) [:incident :vulnerability])))
      (testing "nested sub-field added into a non-keyword field should be ignored"
        (let [es-mappings (-> es-mappings
                              (update-in
                               [:incident :title]
                               assoc :type "text" :fields {:ignored-and-sad {:type "text"}}))]
          (is (= expected-result
                 (select-keys (digest-searchable-map es-mappings) [:incident :vulnerability]))))))))
