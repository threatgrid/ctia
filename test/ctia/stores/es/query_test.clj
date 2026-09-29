(ns ctia.stores.es.query-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ctia.domain.access-control :as ac]
   [ctia.entity.entities :as entities]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.es :as es-helpers]
   [ctia.stores.es.query :as sut]))

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
                   "targets.observables.value" "targets.observables.value.text"
                   "id" "id.text"}
   :asset-mapping {"observable.value" "observable.value.text" "source" "source.text" "id" "id.text"}
   :data-table {"source" "source.text" "id" "id.text"}
   :tool {"source" "source.text"
          "id" "id.text"
          "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
          "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :relationship {"source" "source.text" "id" "id.text"}
   :vulnerability {"source" "source.text" "id" "id.text"}
   :judgement {"observable.value" "observable.value.text" "source" "source.text" "id" "id.text"}
   :target-record {"source" "source.text"
                   "targets.observables.value" "targets.observables.value.text"
                   "id" "id.text"}
   :weakness {"source" "source.text" "id" "id.text"}
   :coa {"source" "source.text" "id" "id.text"}
   :attack-pattern {"source" "source.text"
                    "id" "id.text"
                    "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
                    "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :incident {"source" "source.text" "id" "id.text"}
   :event {}
   :indicator {"source" "source.text"
               "id" "id.text"
               "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
               "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :campaign {"source" "source.text" "id" "id.text"}
   :asset-properties {"value" "value.text" "source" "source.text" "id" "id.text"}
   :sighting {"relations.source.value" "relations.source.value.text"
              "relations.related.value" "relations.related.value.text"
              "sensor_coordinates.observables.value" "sensor_coordinates.observables.value.text"
              "observables.value" "observables.value.text"
              "source" "source.text"
              "targets.observables.value" "targets.observables.value.text"
              "id" "id.text"}
   :casebook {"observables.value" "observables.value.text" "source" "source.text" "id" "id.text"}
   :asset {"source" "source.text" "id" "id.text"}
   :identity-assertion {"identity.observables.value" "identity.observables.value.text"
                        "source" "source.text"
                        "id" "id.text"}
   :malware {"labels" "labels.text"
             "x_mitre_aliases" "x_mitre_aliases.text"
             "source" "source.text"
             "id" "id.text"
             "kill_chain_phases.kill_chain_name" "kill_chain_phases.kill_chain_name.text"
             "kill_chain_phases.phase_name" "kill_chain_phases.phase_name.text"}
   :note {"source" "source.text", "id" "id.text"}
   :actor {"source" "source.text" "id" "id.text"}})

(deftest searchable-fields-map-test
  (testing "returns correct mapping for every entity"
    (is (= expected-mapping-per-entity
           (->> (all-entities-es-mapping)
                (map (fn [[k v]]
                       (hash-map k (sut/searchable-fields-map v))))
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
                                              (hash-map k (sut/searchable-fields-map v))))
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

(deftest find-write-restriction-query-part-test
  ;; Pin the exact clause set emitted by the write/delete access-control
  ;; filter. `write-restriction-should-clauses` is the single source shared by
  ;; both the read and the write filters (XFV-120), and nothing else in the
  ;; code distinguishes which disjuncts belong to which policy. This test forces
  ;; any edit to that shared vector to be acknowledged on the write side: a
  ;; read-side widening (e.g. a new public-visibility disjunct) that landed
  ;; silently on the delete path would break this assertion.
  (let [ident {:login "Foo" :groups ["Bar"]}
        expected {:bool
                  {:minimum_should_match 1
                   :should [;; document owner (TLP-independent)
                            {:bool {:filter [{:term {"owner" "foo"}}
                                             {:terms {"groups" ["bar"]}}]}}
                            ;; explicit grants
                            {:term {"authorized_users" "foo"}}
                            {:terms {"authorized_groups" ["bar"]}}
                            ;; same-group records at TLP amber or below
                            {:bool {:must [{:terms {"tlp" (conj ac/public-tlps "amber")}}
                                           {:terms {"groups" ["bar"]}}]}}
                            ;; same-group owner records at TLP red (redundant
                            ;; with the owner clause; kept for read parity)
                            {:bool {:must [{:term {"tlp" "red"}}
                                           {:term {"owner" "foo"}}
                                           {:terms {"groups" ["bar"]}}]}}]}}]
    (testing "emitted write filter matches the pinned clause set, login/groups lower-cased"
      (is (= expected (sut/find-write-restriction-query-part ident))))
    (testing "the write filter never grants blanket public-TLP visibility"
      ;; The security property of XFV-120: unlike the read filter under
      ;; max-record-visibility=everyone, the write filter must not contain a
      ;; disjunct that matches any white/green document regardless of owner.
      (let [should (get-in (sut/find-write-restriction-query-part ident)
                           [:bool :should])]
        (is (not (some #{{:terms {"tlp" ac/public-tlps}}} should)))))))

(deftest rename-search-fields-map-test
  (doseq [es-version [7]]
    (helpers/with-properties
      (concat  ["ctia.feature-flags" "translate-searchable-fields:true"
                "ctia.store.es.default.port" (+ 9200 es-version)
                "ctia.store.es.default.version" es-version]
               es-helpers/basic-auth-properties)
      (helpers/fixture-ctia-with-app
       (fn [app]
         (let [conn-state (:state (helpers/get-store app :sighting))]
           (is (= ["source.text" "title"]
                  (sut/rename-search-fields conn-state [:source :title])))))))))
