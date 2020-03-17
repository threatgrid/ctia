(ns ctia.task.update-mapping-test
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.conn :as conn]
            [clojure.string :as string]
            [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
            [ctia.task.rollover :as rollover]
            [ctia.entity.incident :as incident]
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
  (let [index-map (into {}
                        (map (juxt identity (partial es-index/get conn)))
                        istrs)]
    (testing "Each query yields at least one index"
      (is (every? (comp seq index-map) istrs)))
    (doseq [[index-name idxs] index-map
            index idxs]
      (f index-name index))))

(defn- update-mapping-stores!-test-helper
  "If aliased? is true, test update-mapping-stores! with an aliased store.
  If aliased? is false, test update-mapping-stores! with an unaliased store.
  
  This function builds up a `reduce` to functionally step through a sequence of
  of stores, rollovers, and update-mapping-stores! calls, and test intermediate states.
  "
  [aliased?]
  (let [field-mapping {:type "keyword", :include_in_all false, :normalizer "lowercase_normalizer"}

        ; set up connection
        store-properties (cond-> {:entity :incident
                                  :indexname "ctia_incident"
                                  :host "localhost"
                                  :port 9200}
                           aliased? (assoc :props {:aliased true
                                                   :write-index "ctia_incident-write"
                                                   ; cheap trick to rollover store without adding docs
                                                   :rollover {:max_docs 0}}))
        {:keys [conn] :as state} (init/init-es-conn! store-properties)

        index-names (cond-> ["ctia_incident"]
                      aliased? (conj "ctia_incident-write"))

        ; minimal store (same shape as @ctia.store/stores)
        stores {:incident
                [((:es-store incident/incident-entity)
                  {:index "ctia_incident"
                   :props (:props store-properties)
                   :config {}
                   :conn conn})]}

        ; TestingStep = {:present #{Kw}, :absent #{Kw}, (optional-field :add-field) Kw}
        ; testing-procedure : [TestingStep]
        testing-procedure [; 1. ensure both fields are absent
                           {:present #{}
                            :absent #{:new-field1 :new-field2}}
                           ; 2. add first field, ensure it is present
                           {:add-field :new-field1
                            :present #{:new-field1}
                            :absent #{:new-field2}}
                           ; 3. add second field, ensure it is also present
                           {:add-field :new-field2
                            :present #{:new-field1 :new-field2}
                            :absent #{}}]

        ; Store TestingStep -> Store
        testing-fn (fn [stores {:keys [present absent add-field]}]
                     (let [stores (cond-> stores
                                    add-field (assoc-in
                                                [:incident 0 :state :config :mappings
                                                 "incident" :properties add-field]
                                                field-mapping))
                           _ (task/update-mapping-stores! stores)
                           _ (when aliased?
                               (let [{:keys [nb-errors] :as responses} (rollover/rollover-stores stores)]
                                 (testing "Rollover completed without errors"
                                   (is (= 0 nb-errors)
                                       (pr-str responses)))
                                 (testing ":incident store successfully rolled over"
                                   (is (get-in responses [:incident :rolled_over])
                                       (pr-str responses)))))

                           _ (inspect-indices
                               conn
                               index-names
                               (fn [index-name {:keys [mappings] :as index}]
                                 (doseq [f absent]
                                   (testing (str "Index " index-name " doesn't map field " f)
                                     (is (nil? (get-in mappings [:incident :properties f])))))
                                 (doseq [f present]
                                   (testing (str "Index " index-name " maps field " f)
                                     (is (= field-mapping (get-in mappings [:incident :properties f])))))))]
                       stores))

        ; the actual testing
        _ (reduce testing-fn stores testing-procedure)]))

; separated to take advantage of fixtures
(deftest update-mapping-stores!-aliased-test   (update-mapping-stores!-test-helper true))
(deftest update-mapping-stores!-unaliased-test (update-mapping-stores!-test-helper false))
