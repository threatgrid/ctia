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
            [_index-kw_ index] idxs]
      (f index-name index))))

; TestingStep = {:present {Kw Mapping}
;                :absent [Kw]
;                (optional-entry :add-field) (tuple Kw Mapping)}
;
; gen-testing-plan : NatInt -> [TestingStep]
(defn- gen-testing-plan
  "Generates a testing plan, a sequence of steps that progressively
  adds `nb-new-fields` new fields and checks they were actually added."
  [nb-new-fields]
  {:pre [(nat-int? nb-new-fields)]}
  (let [new-fields (vec (shuffle (map #(keyword nil (str "new-field" %)) (range nb-new-fields))))
        _ (assert (or (empty? new-fields)
                      (apply distinct? new-fields)))
        dummy-field-mapping {:type "keyword", :include_in_all false, :normalizer "lowercase_normalizer"}
        new-field-mappings (zipmap new-fields (repeat dummy-field-mapping))]
    (cons
      {:present {}
       :absent new-fields}
      (map
        (fn [i]
          {:add-field (find new-field-mappings (nth new-fields i))
           :present (select-keys new-field-mappings (subvec new-fields 0 (inc i)))
           :absent (subvec new-fields (inc i))})
        (range (count new-fields))))))

(defn- update-mapping-stores!-test-helper
  "If aliased? is true, test update-mapping-stores! with an aliased store.
  If aliased? is false, test update-mapping-stores! with an unaliased store.
  
  This function builds up a `reduce` to functionally step through a sequence of
  of stores, rollovers, and update-mapping-stores! calls, and test intermediate states.
  "
  [aliased?]
  (let [; set up connection
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

        testing-plan (gen-testing-plan 10)

        ; update-mapping-stores! and rollover-stores should be able to run in
        ; any order
        fs (cond-> {:update-mapping-stores! task/update-mapping-stores!}
             aliased?
             (assoc
               :rollover-stores
               #(let [{:keys [nb-errors] :as responses} (rollover/rollover-stores %)]
                  (testing "Rollover completed without errors"
                    (is (= 0 nb-errors)
                        (pr-str responses)))
                  (testing ":incident store successfully rolled over"
                    (is (get-in responses [:incident :rolled_over])
                        (pr-str responses))))))

        ; Store TestingStep -> Store
        testing-fn (fn [stores {:keys [present absent add-field] :as _step_}]
                     (let [chosen-order (if aliased?
                                          ;FIXME shuffle
                                          [:rollover-stores :update-mapping-stores!]
                                          (shuffle (keys fs)))
                           stores (cond-> stores
                                    add-field (assoc-in
                                                [:incident 0 :state :config :mappings
                                                 "incident" :properties (nth add-field 0)]
                                                (nth add-field 1)))]
                       (testing (str "Incides should correctly update with ordering " (vec chosen-order))
                         (run! (comp #(% stores) fs) chosen-order)
                         (inspect-indices
                           conn
                           index-names
                           (fn [index-name {:keys [mappings] :as _index_}]
                             (doseq [f absent]
                               (testing (str "Index " index-name " should not map field " f)
                                 (is (nil? (get-in mappings [:incident :properties f])))))
                             (doseq [[f expected-mapping] present]
                               (testing (str "Index " index-name " should map field " f)
                                 (is (= expected-mapping
                                        (get-in mappings [:incident :properties f]))))))))
                       stores))

        ; the actual testing
        _ (reduce testing-fn stores testing-plan)]))

; separated to take advantage of fixtures
(deftest update-mapping-stores!-aliased-test   (update-mapping-stores!-test-helper true))
(deftest update-mapping-stores!-unaliased-test (update-mapping-stores!-test-helper false))
