(ns ctia.bundle.routes-test
  (:refer-clojure :exclude [get])
  (:require [ctim.schemas.common :refer [ctim-schema-version]]
            [clj-momo.lib.es.index :as es-index]
            [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure
             [set :as set]
             [test :as t :refer [deftest is join-fixtures testing use-fixtures]]]
            [ctia.bulk.core :as bulk]
            [ctia.bundle.core :as core]
            [ctia.store :refer [stores]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers
             [core :as helpers :refer [deep-dissoc-entity-ids
                                       get
                                       post
                                       delete
                                       entity->short-id]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.examples.bundles :refer [bundle-maximal]]))

(defn fixture-properties [t]
  (helpers/with-properties ["ctia.http.bulk.max-size" 1000
                            "ctia.http.bundle.export.max-relationships" 500]
    (t)))

(defn fixture-find-by-external-ids-limit [t]
  (with-redefs [core/find-by-external-ids-limit 5]
    (t)))

(use-fixtures :once
  (join-fixtures
   [mth/fixture-schema-validation
    helpers/fixture-properties:clean
    fixture-properties
    fixture-find-by-external-ids-limit
    whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(defn mk-sighting
  [n]
  {:id (id/make-transient-id nil)
   :external_ids [(str "ctia-sighting-" n)]
   :description (str "description: sighting-" n)
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :observed_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :schema_version "1.0.0"
   :count 1
   :source "source"
   :sensor "endpoint.sensor"
   :confidence "High"})

(defn mk-indicator
  [n]
  {:id (id/make-transient-id nil)
   :external_ids [(str "ctia-indicator-" n)]
   :title (str "indicator-" n)
   ;; simulate an outdated schema version -- should be ignored by the importer
   :schema_version "0.4.2"
   :description (str "description: indicator-" n)
   :producer "producer"
   :indicator_type ["C2" "IP Watchlist"]
   :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-casebook []
  {:id (id/make-transient-id nil)})

(defn mk-incident []
  {:id (id/make-transient-id nil)
   :incident_time {:opened #inst "2016-02-11T00:40:48.212-00:00"}
   :status "Open"
   :confidence "High"})

(defn mk-judgement []
  {:observable {:type "ip",
                :value "10.0.0.1"}
   :source "source"
   :priority 99
   :confidence "High"
   :severity "Medium"})

(defn mk-relationship
  [n source target relation-type]
  {:id (id/make-transient-id nil)
   :title (str "title" n)
   :description (str "description-" n)
   :short_description "short desc"
   :revision 1
   :external_ids [(str "ctia-relationship-" n)]
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :language "language"
   :source "source"
   :schema_version "1.1.1"
   :source_uri "http://example.com"
   :relationship_type relation-type
   :source_ref (:id source)
   :target_ref (:id target)})

(deftest valid-external-id-test
  (is (= "ctia-1"
         (core/valid-external-id ["invalid-1" "invalid-2"  "ctia-1"]
                                 ["ctia-" "cisco-"])))
  (is (nil? (core/valid-external-id ["invalid-1" "invalid-2"  "ctia-1" "cisco-1"]
                                    ["ctia-" "cisco-"]))))

(defn validate-entity-record
  [{:keys [id original_id action external_id]
    entity-type :type
    :or {entity-type :unknown}
    :as result}
   original-entity]
  (testing (str "Entity " external_id)
    (is (= (:id original-entity) original_id)
        "The orignal ID is in the result")
    (is (contains? (set (:external_ids original-entity))
                   external_id)
        "The external ID is in the result")
    (testing "External ID"
      (let [response (get (format "ctia/%s/external_id/%s"
                                  (name entity-type)
                                  external_id)
                          :headers {"Authorization" "45c1f5e3f05d0"})
            [entity :as entities] (:parsed-body response)]
        (is (= 1 (count entities))
            "Only one entity is linked to the external ID")
        (is (= id (:id entity))
            "The submitted entity is linked to the external ID")))
    (testing "Entity values"
      (when id
        (let [response (get (format "ctia/%s/%s"
                                    (name entity-type)
                                    (encode id))
                            :headers {"Authorization" "45c1f5e3f05d0"})
              entity (:parsed-body response)]
          (is (= (assoc original-entity
                        :id id
                        :schema_version ctim-schema-version)
                 (select-keys entity (keys original-entity)))))))))

(defn find-result-by-original-id
  "Find an entity result in the bundle result with its original ID"
  [bundle-result original-id]
  (->> bundle-result
       :results
       (filter #(= (:original_id %) original-id))
       first))

(defn resolve-ids
  "Resolves transient IDs in the target_ref and the source_ref
  of a relationship"
  [bundle-result
   {:keys [source_ref target_ref] :as relationship}]
  (let [by-original-id (set/index (:results bundle-result)
                                  [:original_id])
        source-result (first
                       (clojure.core/get by-original-id
                                         {:original_id source_ref}))
        target-result (first
                       (clojure.core/get by-original-id
                                         {:original_id target_ref}))]

    (assoc relationship
           :source_ref
           (:id source-result source_ref)
           :target_ref
           (:id target-result target_ref))))

(defn with-modified-description
  [entity]
  (update entity :description str "-modified"))

(defn count-bundle-entities
  "Returns a map containing the number of entities
   per entity type
   Ex:
   {:attack-pattern 1
    :indicator 2}"
  [bundle]
  (->> (select-keys bundle core/bundle-entity-keys)
       (map (fn [[k v]]
              [(bulk/entity-type-from-bulk-key k) (count v)]))
       (into {})))

(defn count-bundle-result-entities
  "Returns a map containing the number of entities with the given result.
   The map is indexed by entity type.
  Ex:
   {:attack-pattern 1
    :indicator 2}"
  [import-result result]
  (->> import-result
       (filter #(= result (:result %)))
       (group-by :type)
       (map (fn [[k v]] [k (count v)]))
       (into {})))


(deftest bundle-import-wait_for-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [headers {"Authorization" "45c1f5e3f05d0"}
           uuid-fn #(str (java.util.UUID/randomUUID))
           get-statuses (fn [wait_for]
                          (let [indicators [(mk-indicator (uuid-fn))
                                            (mk-indicator (uuid-fn))]
                                sightings [(mk-sighting (uuid-fn))
                                           (mk-sighting (uuid-fn))]
                                relationships (map (fn [indicator sighting]
                                                     (mk-relationship (uuid-fn)
                                                                      indicator
                                                                      sighting
                                                                      "indicates"))
                                                   indicators
                                                   sightings)
                                bundle {:type "bundle"
                                        :source "source"
                                        :indicators (set indicators)
                                        :sightings (set sightings)
                                        :relationships (set relationships)}
                                path (cond-> "ctia/bundle/import"
                                       (boolean? wait_for) (str "?wait_for=" wait_for))
                                response (post path
                                               :body bundle
                                               :headers {"Authorization" "45c1f5e3f05d0"})
                                statuses (->> (:parsed-body response)
                                              :results
                                              (map #(get (format "ctia/%s/%s"
                                                                 (-> % :type name)
                                                                 (-> % entity->short-id))
                                                         :headers headers))
                                              (map :status))]
                            (is (= 200 (:status response)))
                            statuses))]

       (is (every? #(= 200 %)
                   (get-statuses true))
           "Bundle import should wait for index refresh when wait_for is true")
       (is (some (fn [statuses]
                   (some #(= 404 %) statuses))
                 (repeatedly 2 #(get-statuses false)))
           "Bundle imports should not wait for index refresh when wait_for is false")
       (testing "Configured ctia.store.bundle-refresh value is applied when wait_for is not specified"
         (if (= "false" (get-in @properties [:ctia :store :bundle-refresh]))
           (is (some (fn [statuses]
                       (some #(= 404 %) statuses))
                     (repeatedly 2 #(get-statuses nil))))
           (is (every? #(= 200 %)
                       (get-statuses true)))))))))

(deftest bundle-import-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [indicators [(mk-indicator 0)
                       (mk-indicator 1)]
           sightings [(mk-sighting 0)
                      (mk-sighting 1)]
           relationships (map (fn [idx indicator sighting]
                                (mk-relationship idx indicator
                                                 sighting "indicates"))
                              (range)
                              indicators
                              sightings)]
       (testing "Import bundle with all entity types"
         (let [new-bundle (deep-dissoc-entity-ids bundle-maximal)
               response (post "ctia/bundle/import"
                              :body new-bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (is (= 200 (:status response)))
           (is (= (count-bundle-entities new-bundle)
                  (count-bundle-result-entities (:results bundle-result)
                                                "created"))
               "All entities are created")))
       (testing "Create"
         (let [bundle {:type "bundle"
                       :source "source"
                       :indicators (set indicators)
                       :sightings (set sightings)
                       :relationships (set relationships)}
               response (post "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (is (= 200 (:status response)))

           (is (every? #(= "created" %)
                       (map :result (:results bundle-result)))
               "All entities are created")

           (doseq [entity (concat indicators
                                  sightings
                                  (map #(resolve-ids bundle-result %)
                                       relationships))]
             (validate-entity-record
              (find-result-by-original-id bundle-result (:id entity))
              entity))))
       (testing "Update"
         (let [bundle
               {:type "bundle"
                :source "source"
                :indicators (set (map with-modified-description indicators))
                :sightings (set (map with-modified-description sightings))
                :relationships (set (map with-modified-description relationships))}
               response (post "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (is (= 200 (:status response)))

           (is (pos? (count (:results bundle-result))))

           (is (every? #(= "exists" %)
                       (map :result (:results bundle-result)))
               "All existing entities are not updated")

           (doseq [entity (concat indicators
                                  sightings
                                  (map #(resolve-ids bundle-result %)
                                       relationships))]
             (validate-entity-record
              (find-result-by-original-id bundle-result (:id entity))
              entity))))
       (testing "Update and create"
         (let [indicator (mk-indicator 2000)
               sighting (first sightings)
               relationship (mk-relationship 2000
                                             indicator
                                             sighting
                                             "indicates")
               bundle
               {:type "bundle"
                :source "source"
                :indicators [indicator]
                :sightings [sighting]
                :relationships [relationship]}
               response (post "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (is (= 200 (:status response)))

           (is (pos? (count (:results bundle-result))))

           (doseq [entity [indicator sighting
                           (resolve-ids bundle-result relationship)]]
             (validate-entity-record
              (find-result-by-original-id bundle-result (:id entity))
              entity))))
       (testing "Bundle with missing entities"
         (let [relationship (mk-relationship 2001
                                             (mk-indicator 2001)
                                             (first sightings)
                                             "indicates")
               bundle {:type "bundle"
                       :source "source"
                       :relationships [relationship]}
               response-create (post "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-create (:parsed-body response-create)]
           (is (= 200 (:status response-create)))
           (is (= [{:original_id (:id relationship),
                    :result "error",
                    :type :relationship,
                    :error (str "A relationship cannot be created if a "
                                "source or a target ref is still a transient "
                                "ID (The source or target entity is probably "
                                "not provided in the bundle)")}]
                  (filter (fn [r] (= (:result r) "error"))
                          (:results bundle-result-create)))
               (str "A relationship cannot be created if the source and the "
                    "target entities referenced by a transient ID are not "
                    "included in the bundle."))))
       (testing "Custom external prefix keys"
         (let [bundle {:type "bundle"
                       :source "source"
                       :indicators (hash-set
                                    (assoc (first indicators)
                                           :external_ids
                                           ["custom-2"]))}
               response-create (post "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-create (:parsed-body response-create)
               response-update (post "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-update (:parsed-body response-update)]
           (is (= 200 (:status response-create)))
           (is (= 200 (:status response-update)))

           (is (pos? (count (:results bundle-result-create))))
           (is (pos? (count (:results bundle-result-update))))

           (is (every? #(= "created" %)
                       (map :result (:results bundle-result-create)))
               "All new entities are created")
           (is (every? #(= "exists" %)
                       (map :result (:results bundle-result-update)))
               "All existing entities are not updated")))
       (testing "Partial results with errors"
         (let [indicator-store-state (-> @stores :indicator first :state)
               indexname (:index indicator-store-state)
               ;; close indicator index to produce ES errors on that store
               _ (es-index/close! (:conn indicator-store-state) indexname)
               bundle {:type "bundle"
                       :source "source"
                       :sightings [(mk-sighting 10)
                                   (mk-sighting 11)]
                       ;; Remove external_ids to avoid errors
                       ;; coming from the search operation
                       :indicators [(dissoc (mk-indicator 10)
                                            :external_ids)]}
               response-create (post "ctia/bundle/import"
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-create (:parsed-body response-create)]
           (is (= 200 (:status response-create)))
           (is (every? #(= "created" %)
                       (->> (:results bundle-result-create)
                            (filter #(= "sighting" %))
                            (map :result)))
               "All valid entities are created")
           (doseq [entity (:sightings bundle)]
             (validate-entity-record
              (find-result-by-original-id bundle-result-create (:id entity))
              entity))
           (let [indicators (filter
                             #(= :indicator (:type %))
                             (:results bundle-result-create))]
             (is (not (empty? indicators))
                 "The result collection for indicators is not empty")
             (is (every? #(contains? % :error) indicators)))
           ;; reopen index to enable cleaning
           (es-index/open! (:conn indicator-store-state) indexname)))))))

(deftest bundle-import-errors-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [bundle {:source "foo"
                   :indicators [(assoc (mk-indicator 1)
                                       :valid_time
                                       {:start_time #inst "2042-05-11T00:40:48.212-00:00"
                                        :end_time #inst "4242-07-11T00:40:48.212-00:00"})]}
           response-create (post "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
           bundle-result-create (:parsed-body response-create)]

       (is (= 200 (:status response-create)))
       (is (= {:results
               [{:original_id (:id (-> bundle :indicators first)),
                 :result "error",
                 :type :indicator,
                 :external_id "ctia-indicator-1",
                 :error "Entity validation Error",
                 :msg "In: [:valid_time :end_time] val: #inst \"4242-07-11T00:40:48.212-00:00\" fails spec: :new-indicator.valid_time/end_time at: [:valid_time :end_time] predicate: (inst-in-range? #inst \"1970-01-01T00:00:00.000-00:00\" #inst \"2525-01-01T00:01:00.000-00:00\" %)\n"}]}
              (:parsed-body response-create)))))))

(deftest find-by-external-ids-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [;; See fixture-find-by-external-ids-limit
           nb-entities (+ core/find-by-external-ids-limit 5)
           bundle {:type "bundle"
                   :source "source"
                   :indicators (set (map mk-indicator (range nb-entities)))}
           response-create (post "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
           bundle-result-create (:parsed-body response-create)
           response-update (post "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
           bundle-result-update (:parsed-body response-update)]
       (is (= 200 (:status response-create)))
       (is (= 200 (:status response-update)))
       (is (= nb-entities
              (count (:results bundle-result-create))))
       (is (= nb-entities
              (count (:results bundle-result-update))))

       (is (every? #(= "created" %)
                   (map :result (:results bundle-result-create)))
           "All new entities are created")
       (is (every? #(= "exists" %)
                   (map :result (:results bundle-result-update)))
           "All existing entities are not updated")))))


(def bundle-fixture-1
  (let [indicators [(mk-indicator 0)
                    (mk-indicator 1)]
        sightings [(mk-sighting 0)]
        relationships (map (fn [idx indicator]
                             (mk-relationship idx indicator
                                              (first sightings) "indicates"))
                           (range)
                           indicators)]
    {:type "bundle"
     :source "source"
     :indicators (set indicators)
     :sightings (set sightings)
     :relationships (set relationships)}))

(def bundle-fixture-2
  (let [indicators (map mk-indicator (range 2 402))
        sightings [(mk-sighting 1)]
        relationships (map (fn [idx indicator]
                             (mk-relationship idx indicator
                                              (first sightings) "indicates"))
                           (range 100 1000)
                           indicators)]
    {:type "bundle"
     :source "source"
     :indicators (set indicators)
     :sightings (set sightings)
     :relationships (set relationships)}))

(deftest bundle-export-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "filtering on entities ids"
       (let [bundle-res-1
             (:parsed-body (post "ctia/bundle/import"
                                 :body bundle-fixture-1
                                 :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-res-2
             (:parsed-body (post "ctia/bundle/import"
                                 :body bundle-fixture-2
                                 :headers {"Authorization" "45c1f5e3f05d0"}))
             sighting-id-1
             (some->> bundle-res-1
                      :results
                      (group-by :type)
                      :sighting
                      first
                      :id)
             sighting-id-2
             (some->> bundle-res-2
                      :results
                      (group-by :type)
                      :sighting
                      first
                      :id)
             bundle-get-res-1
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids sighting-id-1}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-2
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids sighting-id-2}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-3
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-4
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]
                                  :include_related_entities false}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-5
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]
                                  :related_to ["target_ref" "source_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-post-res
             (:parsed-body
              (post "ctia/bundle/export"
                    :body {:ids [sighting-id-1
                                 sighting-id-2]}
                    :query-params {:related_to ["target_ref" "source_ref"]}
                    :headers {"Authorization" "45c1f5e3f05d0"}))]

         (is (= 1 (count (:sightings bundle-get-res-1))))
         (is (= 2 (count (:relationships bundle-get-res-1))))
         (is (= 2 (count (:indicators bundle-get-res-1))))

         (is (= 1 (count (:sightings bundle-get-res-2))))
         (is (= 400 (count (:relationships bundle-get-res-2))))
         (is (= 400 (count (:indicators bundle-get-res-2))))

         (is (= 2 (count (:sightings bundle-get-res-3))))
         (is (= 402 (count (:relationships bundle-get-res-3))))
         (is (= 402 (count (:indicators bundle-get-res-3))))

         (is (= 2 (count (:sightings bundle-get-res-4))))
         (is (nil? (:indicators bundle-get-res-4)))
         (is (= 402 (count (:relationships bundle-get-res-4))))

         (is (= bundle-get-res-3 bundle-get-res-5)
             "default related_to value should be [:source_ref :target_ref]")
         (is (= bundle-get-res-5 bundle-post-res)
             "POST and GET bundle/export routes must return the same results"))))))

(deftest bundle-export-with-unreachable-entities
  (testing "external and deleted entities in fetched relationships should be ignored"
    (test-for-each-store
     (fn []
       (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
       (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                           "foouser"
                                           "foogroup"
                                           "user")
       (let [sighting-1 (mk-sighting 1)
             indicator-1 (mk-indicator 1)
             indicator-2 (mk-indicator 2)
             relationship-1 (mk-relationship 1 sighting-1 indicator-1 "member-of")
             relationship-2 (mk-relationship 2 sighting-1 indicator-2 "member-of")
             relationship-3 ;;relationship to external entity
             {:type "relationship"
              :source_ref (:id sighting-1)
              :relationship_type "indicates"
              :target_ref "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"}
             bundle {:type "bundle"
                     :source "source"
                     :sightings #{sighting-1}
                     :indicators #{indicator-1 indicator-2}
                     :relationships #{relationship-1 relationship-2 relationship-3}}
             bundle-import (post "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
             id-map (some->> bundle-import
                             :parsed-body
                             :results
                             (map (fn [{:keys [original_id id]}]
                                    [original_id id]))
                             (into {}))
             indicator-1-final-id (clojure.core/get id-map (:id indicator-1))
             indicator-2-final-id (clojure.core/get id-map (:id indicator-2))
             sighting-1-final-id (clojure.core/get id-map (:id sighting-1))
             delete-indicator (delete (format "ctia/indicator/%s" (id/str->short-id indicator-1-final-id))
                                      :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-export (get "ctia/bundle/export"
                                :query-params {:ids [sighting-1-final-id]}
                                :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-export-body (:parsed-body bundle-export)]
         (is (= 200 (:status bundle-import)) "Import request status should be 200")
         (is (= 200 (:status bundle-export)) "Export request status should be 200")
         (is (= 204 (:status delete-indicator)) "Delete indicator request status should be 204")
         (is (= (list indicator-2-final-id) (->> (:indicators bundle-export-body)
                                                 (map :id))))
         (is (= 3 (count (:relationships bundle-export-body))))
         (is (= 1 (count (:sightings bundle-export-body)))))))))

(def bundle-related-fixture
  (let [indicator-1 (mk-indicator 1)
        indicator-2 (mk-indicator 2)
        indicator-3 (mk-indicator 3)
        sighting-1 (mk-sighting 1)
        sighting-2 (mk-sighting 2)
        relationship-1 (mk-relationship 1 sighting-1 indicator-1 "member-of")
        relationship-2 (mk-relationship 2 sighting-1 indicator-2 "member-of")
        relationship-3 (mk-relationship 3 sighting-2 indicator-1 "member-of")
        relationship-4 (mk-relationship 4 sighting-2 indicator-3 "member-of")]
    {:type "bundle"
     :source "source"
     :indicators #{indicator-1
                   indicator-2
                   indicator-3}
     :sightings #{sighting-1 sighting-2}
     :relationships #{relationship-1
                      relationship-2
                      relationship-3
                      relationship-4}}))


(def fixture-many-relationships
  (let [indicators (map mk-indicator (range 300))
        sighting (mk-sighting 0)
        relationships (map
                       (fn [idx indicator]
                         (mk-relationship idx indicator sighting "indicates"))
                       (range)
                       (concat indicators indicators))]
    {:type "bundle"
     :source "source"
     :indicators (set indicators)
     :sightings #{sighting}
     :relationships (set relationships)}))

(deftest bundle-max-relationships-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "testing max number of retrieved relationships"
       (let [imported-bundle (post "ctia/bundle/import"
                                   :body fixture-many-relationships
                                   :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-ids (->> (-> imported-bundle :parsed-body :results)
                               (filter #(= (:type %) :sighting))
                               (map :id))
             exported-bundle
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids sighting-ids}
                   :headers {"Authorization" "45c1f5e3f05d0"}))]
         (is (= 500 (count (:relationships exported-bundle)))))))))


(deftest bundle-export-related-to-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "testing related_to filter: relationships should be joined only on attributes specified by related_to values (source_ref and/or target_ref)"
       (let [bundle-res
             (:parsed-body (post "ctia/bundle/import"
                                 :body bundle-related-fixture
                                 :headers {"Authorization" "45c1f5e3f05d0"}))

             by-type (->> bundle-res :results (group-by :type))

             [sighting-id-1
              sighting-id-2] (->> (:sighting by-type)
                                  (sort-by :external_id)
                                  (map :id))
             [indicator-id-1
              indicator-id-2
              indicator-id-3] (->> (:indicator by-type)
                                   (sort-by :external_id)
                                   (map :id))
             [relationship-id-1
              relationship-id-2
              relationship-id-3
              relationship-id-4] (->> (:relationship by-type)
                                      (sort-by :external_id)
                                      (map :id))
             bundle-from-source
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1]
                                  :related_to ["source_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-from-target-1
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [indicator-id-1]
                                  :related_to ["target_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-from-target-2
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [indicator-id-2]
                                  :related_to ["target_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))]

         (is (= #{relationship-id-1
                  relationship-id-2} (->> bundle-from-source
                                          :relationships
                                          (map :id)
                                          set)))
         (is (= #{indicator-id-1
                  indicator-id-2} (->> bundle-from-source
                                       :indicators
                                       (map :id)
                                       set)))
         (is (= #{sighting-id-1} (->> bundle-from-source
                                      :sightings
                                      (map :id)
                                      set)))

         (is (= #{indicator-id-1} (->> bundle-from-target-1
                                       :indicators
                                       (map :id)
                                       set)))
         (is (= #{relationship-id-1
                  relationship-id-3} (->> bundle-from-target-1
                                          :relationships
                                          (map :id)
                                          set)))
         (is (= #{sighting-id-1
                  sighting-id-2} (->> bundle-from-target-1
                                      :sightings
                                      (map :id)
                                      set)))

         (is (= #{relationship-id-2} (->> bundle-from-target-2
                                          :relationships
                                          (map :id)
                                          set)))
         (is (= #{indicator-id-2} (->> bundle-from-target-2
                                       :indicators
                                       (map :id)
                                       set)))
         (is (= #{sighting-id-1} (->> bundle-from-target-2
                                      :sightings
                                      (map :id)
                                      set))))))))

(defn with-tlp-property-setting [tlp f]
  (with-redefs [ctia.properties/properties
                (-> (deref ctia.properties/properties)
                    (assoc-in [:ctia :access-control :min-tlp] tlp)
                    (assoc-in [:ctia :access-control :default-tlp] tlp)
                    atom)]
    (f)))

(deftest bundle-tlp-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "entities TLP settings are validated"
       (let [sighting (assoc (mk-sighting 1) :tlp "white")
             new-bundle
             {:type "bundle"
              :source "source"
              :sightings #{sighting}}]

         (with-tlp-property-setting "amber"
           #(let [res (post "ctia/bundle/import"
                            :body new-bundle
                            :headers {"Authorization" "45c1f5e3f05d0"})]
              (is (= "Entity Access Control validation Error" (-> (:parsed-body res) :results first :error)))
              (is (= 200 (:status res))))))))))

(deftest bundle-acl-fields-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "Bundle export allows acl fields"
       (let [sighting (assoc (mk-sighting 1) :authorized_users ["foo"])
             judgement (assoc (mk-judgement) :authorized_users ["foo"])
             judgement-post-res (post "ctia/judgement"
                                      :body judgement
                                      :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-post-res (post "ctia/sighting"
                                     :body sighting
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-id (-> sighting-post-res :parsed-body :id)
             judgement-id (-> judgement-post-res :parsed-body :id)
             bundle-get-res (get "ctia/bundle/export"
                                 :query-params {:ids [sighting-id
                                                      judgement-id]}
                                 :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-post-res (post "ctia/bundle/export"
                                   :body {:ids [sighting-id
                                                judgement-id]}
                                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 200 (:status bundle-post-res)))
         (is (= 200 (:status bundle-get-res))))))))

(deftest bundle-export-casebook-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "Bundle export should include casebooks"
       (let [casebook (mk-casebook)
             incident (mk-incident)
             casebook-post-res (post "ctia/casebook"
                                     :body casebook
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             incident-post-res (post "ctia/incident"
                                     :body incident
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             incident-id (-> incident-post-res :parsed-body :id)
             casebook-id (-> casebook-post-res :parsed-body :id)
             incident-short-id (id/str->short-id incident-id)
             link-res (post (str "/ctia/incident/" incident-short-id "/link")
                            :body {:casebook_id casebook-id
                                   :tlp "white"}
                            :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-get-res (get "ctia/bundle/export"
                                 :query-params {:ids [incident-id]}
                                 :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 (:status casebook-post-res)))
         (is (= 201 (:status incident-post-res)))
         (is (= 201 (:status link-res)))
         (is (= 200 (:status bundle-get-res)))
         (is seq (-> bundle-get-res :parsed-body :casebooks)))))))
