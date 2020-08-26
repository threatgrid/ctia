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
            [ctia.store-service :as store-svc]
            [ctia.properties :as p]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.test-helpers
             [core :as helpers :refer [deep-dissoc-entity-ids get post delete]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store]]]
            [ctim.domain.id :as id]
            [ctia.auth :as auth :refer [IIdentity]]
            [ctim.examples.bundles :refer [bundle-maximal]]
            [puppetlabs.trapperkeeper.app :as app]))

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

(defn mk-identity-assertion [n]
  {:id (id/make-transient-id nil)
   :external_ids [(str "ctia-identity-aasertion-" n)]
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :identity {:observables [{:type "ip" :value "100.213.110.122"}]}
   :assertions [{:name "cisco:ctr:device:owner" :value "Bob"}]
   :source "source"})

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

(defn mk-incident [n]
  {:id (id/make-transient-id nil)
   :incident_time {:opened #inst "2016-02-11T00:40:48.212-00:00"}
   :status "Open"
   :external_ids [(str "ctia-incident-" n)]
   :confidence "High"})

(defn mk-judgement []
  {:id (id/make-transient-id nil)
   :observable {:type "ip",
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

(defn validate-entity-record
  [{:keys [id original_id]
    [external_id & _] :external_ids
    entity-type :type
    :or {entity-type :unknown}}
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

(deftest bundle-import-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [app (helpers/get-current-app)
           store-svc (app/get-service app :StoreService)
           indicators [(mk-indicator 0)
                       (mk-indicator 1)]
           sightings [(mk-sighting 0)
                      (mk-sighting 1)]
           identity_assertions [(mk-identity-assertion 0)
                                (mk-identity-assertion 1)]
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
                       :identity_assertions (set identity_assertions)
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
         (let [indicator-store-state (-> @(store-svc/get-stores store-svc) :indicator first :state)
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
             (is (seq indicators)
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
                                 :headers {"Authorization" "45c1f5e3f05d0"})]

       (is (= 200 (:status response-create)))
       (is (= {:results
               [{:original_id (:id (-> bundle :indicators first)),
                 :result "error",
                 :type :indicator,
                 :external_ids ["ctia-indicator-1"],
                 :error "Entity validation Error",
                 :msg "#inst \"4242-07-11T00:40:48.212-00:00\" - failed: (inst-in-range? #inst \"1970-01-01T00:00:00.000-00:00\" #inst \"2525-01-01T00:01:00.000-00:00\" %) in: [:valid_time :end_time] at: [:valid_time :end_time] spec: :new-indicator.valid_time/end_time\n"}]}
              (:parsed-body response-create)))))))

(defrecord FakeIdentity [login groups]
  IIdentity
  (authenticated? [_] true)
  (login [_] login)
  (groups [_] groups)
  (allowed-capabilities [_] #{})
  (capable? [_ _] true)
  (rate-limit-fn [_ _] false))

(deftest all-pages-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [app (helpers/get-current-app)
           store-svc (app/get-service app :StoreService)
           read-store (-> (partial store-svc/read-store store-svc)
                          store-svc/store-service-fn->varargs)

           duplicated-indicators (->> (mk-indicator 0)
                                      (repeat (* 10 core/find-by-external-ids-limit))
                                      (map #(assoc % :id (id/make-transient-id nil))))
           more-indicators (map mk-indicator (range 1 10))
           all-indicators (set (concat duplicated-indicators more-indicators))
           duplicated-external-id (-> duplicated-indicators first :external_ids first)
           all-external-ids (mapcat :external_ids all-indicators)
           bundle {:type "bundle"
                   :source "source"
                   :indicators all-indicators}
           response-create (post "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
           ident (FakeIdentity. "foouser" ["foogroup"])
           matched-entities (core/all-pages :indicator all-external-ids ident read-store)
           max-matched (+ core/find-by-external-ids-limit
                          (count more-indicators))]
       (assert (= 200 (:status response-create)))
       (assert (seq all-external-ids))
       (testing "all-pages should not retrieve more duplicates than find-by-external-ids-limit"
         (is (<= (count matched-entities)
                 max-matched))
         (is (<= (->> matched-entities
                      (filter #(= duplicated-external-id
                                  (-> % :external_ids first)))
                      count)
                 max-matched)))
       (is (= (set all-external-ids)
              (->> matched-entities
                   (mapcat :external_ids)
                   set))
           "all-pages must match at least one entity for each existing external-id")))))

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
                                 :query-params {"external-key-prefixes" "ctia-"}
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
         (is (= 0 (count (:relationships bundle-get-res-4))))

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

(def bundle-graph-fixture
  (let [indicator-1 (mk-indicator 1)
        indicator-2 (mk-indicator 2)
        indicator-3 (mk-indicator 3)
        sighting-1 (mk-sighting 1)
        sighting-2 (mk-sighting 2)
        incident-1 (mk-incident 1)
        incident-2 (mk-incident 2)
        relationship-1 (mk-relationship 1 sighting-1 indicator-1 "indicates")
        relationship-2 (mk-relationship 2 sighting-1 indicator-2 "indicates")
        relationship-3 (mk-relationship 3 sighting-2 indicator-1 "indicates")
        relationship-4 (mk-relationship 4 sighting-2 indicator-3 "indicates")
        relationship-5 (mk-relationship 5 sighting-2 incident-1 "member-of")
        relationship-6 (mk-relationship 6 sighting-1 incident-2 "member-of")
        relationship-7 (mk-relationship 7 sighting-2 incident-2 "member-of")
        relationship-8 (mk-relationship 8 indicator-1 incident-2 "member-of")]
    {:type "bundle"
     :source "source"
     :incidents #{incident-1
                  incident-2}
     :indicators #{indicator-1
                   indicator-2
                   indicator-3}
     :sightings #{sighting-1 sighting-2}
     :relationships #{relationship-1
                      relationship-2
                      relationship-3
                      relationship-4
                      relationship-5
                      relationship-6
                      relationship-7
                      relationship-8}}))

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


(deftest bundle-export-graph-test
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "testing relationships filters"
       (let [bundle-res
             (:parsed-body (post "ctia/bundle/import"
                                 :body bundle-graph-fixture
                                 :headers {"Authorization" "45c1f5e3f05d0"}))

             by-type (->> bundle-res :results (group-by :type))

             [sighting-id-1
              sighting-id-2] (->> (:sighting by-type)
                                  (sort-by :external_ids)
                                  (map :id))

             [indicator-id-1
              indicator-id-2
              indicator-id-3] (->> (:indicator by-type)
                                   (sort-by :external_ids)
                                   (map :id))
             [incident-id-1
              incident-id-2] (->> (:incident by-type)
                                   (sort-by :external_ids)
                                   (map :id))
             [relationship-id-1
              relationship-id-2
              relationship-id-3
              relationship-id-4
              relationship-id-5
              relationship-id-6
              relationship-id-7
              relationship-id-8] (->> (:relationship by-type)
                                      (sort-by :external_ids)
                                      (map :id))
             ;; related to queries
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
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             ;; node type filters
             bundle-sighting-source
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [incident-id-2]
                                  :source_type "sighting"}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-incident-target-get
             (:parsed-body
              (get "ctia/bundle/export"
                   :query-params {:ids [sighting-id-2]
                                  :target_type "incident"}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-incident-target-post
             (:parsed-body
              (post "ctia/bundle/export"
                    :body {:ids [sighting-id-2]}
                    :query-params {:target_type "incident"}
                    :headers {"Authorization" "45c1f5e3f05d0"}))]

         (testing "relationships should be joined only on attributes specified by related_to values (source_ref and/or target_ref)"
           (is (= #{relationship-id-1
                    relationship-id-2
                    relationship-id-6} (->> bundle-from-source
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
                                        set))))
         (testing "source_type and target_type should filter relationships nodes from their type"
           (is (= #{sighting-id-1
                    sighting-id-2} (->> bundle-sighting-source
                                        :sightings
                                        (map :id)
                                        set)))
           (is (= #{relationship-id-6
                    relationship-id-7} (->> bundle-sighting-source
                                            :relationships
                                            (map :id)
                                            set)))
           (is (nil?  (:indicators bundle-sighting-source)))

           (is (= #{incident-id-1
                    incident-id-2} (->> bundle-incident-target-get
                                        :incidents
                                        (map :id)
                                        set)))
           (is (= #{relationship-id-5
                    relationship-id-7} (->> bundle-incident-target-get
                                        :relationships
                                        (map :id)
                                        set)))
           (is (nil?  (:indicators bundle-incident-target-get)))
           (is (= bundle-incident-target-get bundle-incident-target-post))))))))

(defn with-tlp-property-setting [tlp f]
  (helpers/with-config-transformer*
    #(-> %
         (assoc-in [:ctia :access-control :min-tlp] tlp)
         (assoc-in [:ctia :access-control :default-tlp] tlp))
    f))

(deftest bundle-tlp-test
 (with-tlp-property-setting "amber"
 (fn []
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
              :sightings #{sighting}}

             res (post "ctia/bundle/import"
                       :body new-bundle
                       :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= "Entity Access Control validation Error" (-> (:parsed-body res) :results first :error))
             res)
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
             incident (mk-incident 1)
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
