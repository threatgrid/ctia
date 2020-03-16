(ns ctia.task.update-mapping-test
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.conn :as conn]
            [clojure.string :as string]
            [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
            [ctia.task.rollover :as rollover]
            [ctia.store :as store]
            [ctia.stores.es.init :as init]
            [ctia.properties :as props]
            [ctia.task.update-mapping :as task]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]))

(use-fixtures :once
  (join-fixtures [whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(use-fixtures :each
  (join-fixtures [helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(defn- inspect-indices [conn istrs f]
  (let [idxs (map (partial es-index/get conn) istrs)]
    (testing "Each query yields at least one index"
      (is (every? seq idxs)))
    (run! (comp f val) (apply concat idxs))))

(defn- update-mapping-stores!-test-helper
  "Iff aliased? is true, test update-mapping-stores! with an aliased store."
  [aliased?]
  (let [new-field-mapping {:type "keyword", :include_in_all false, :normalizer "lowercase_normalizer"}

        ; set up connection
        props (cond-> {:entity :incident
                       :indexname "ctia_incident"
                       :host "localhost"
                       :port 9200}
                aliased? (assoc :props {:aliased true
                                        :write-index "ctia_incident-write"}))
        {:keys [conn] :as state} (init/init-es-conn! props)

        index-names (cond-> ["ctia_incident"]
                      aliased? (conj "ctia_incident-write"))

        ;ensure new field is absent
        _ (inspect-indices
            conn
            index-names
            (fn [{:keys [aliases mappings] :as index}]
              (when aliased?
                (testing "Indices alias each other"
                  (is (= aliases {:ctia_incident {}, :ctia_incident-write {}}))))
              (testing "Indices don't include new mapping"
                (is (nil? (get-in mappings [:incident :properties :new-field]))))))

        ; add field
        _ (task/update-mapping-store state {"incident"
                                            {:properties
                                             {"new-field" new-field-mapping}}})

        ; ensure new field mapped correctly
        _ (inspect-indices
            conn
            index-names
            (fn [{:keys [aliases mappings]}]
              (when aliased?
                (testing "Indices alias each other"
                  (is (= aliases {:ctia_incident {}, :ctia_incident-write {}}))))
              (testing "Indices include new mapping"
                (is (= (get-in mappings [:incident :properties :new-field])
                       new-field-mapping)))))]))

; separated to take advantage of fixtures
(deftest update-mapping-stores!-aliased-test   (update-mapping-stores!-test-helper true))
(deftest update-mapping-stores!-unaliased-test (update-mapping-stores!-test-helper false))
