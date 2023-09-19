(ns ctia.bundle.routes-test
  (:require
   [clj-momo.test-helpers.http :refer [encode]]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.auth :as auth :refer [IIdentity]]
   [ctia.auth.capabilities :refer [all-capabilities]]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.core :as core]
   [ctia.bundle.routes :as bundle.routes]
   [ctia.test-helpers.core :as helpers
    :refer [deep-dissoc-entity-ids GET PATCH POST DELETE]]
   [ctia.test-helpers.http :refer [app->APIHandlerServices]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.domain.id :as id]
   [ctim.examples.asset-properties :refer [asset-properties-maximal]]
   [ctim.examples.assets :refer [asset-maximal]]
   [ctim.examples.bundles :refer [bundle-minimal bundle-maximal]]
   [ctim.examples.incidents :refer [incident-maximal]]
   [ctim.examples.target-records :refer [target-record-maximal]]
   [ctim.schemas.common :refer [ctim-schema-version]]
   [ductile.index :as es-index]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.core :as s]
   [schema.test :refer [validate-schemas]]))

(defn fixture-properties [t]
  (helpers/with-properties ["ctia.http.bulk.max-size" 1000
                            "ctia.http.bundle.export.max-relationships" 500]
    (t)))

(defn fixture-find-by-external-ids-limit [t]
  (with-redefs [core/find-by-external-ids-limit 5]
    (t)))

(use-fixtures :once
  (join-fixtures
   [validate-schemas
    fixture-properties
    fixture-find-by-external-ids-limit
    whoami-helpers/fixture-server]))

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
  [app
   {:keys [id original_id]
    [external_id & _] :external_ids
    entity-type :type
    :or {entity-type :unknown}}
   original-entity]
  (testing (str "Entity " external_id)
    (is (= (:id original-entity) original_id)
        "The original ID is in the result")
    (is (contains? (set (:external_ids original-entity))
                   external_id)
        "The external ID is in the result")
    (testing "External ID"
      (let [response (GET app
                          (format "ctia/%s/external_id/%s"
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
        (let [response (GET app
                            (format "ctia/%s/%s"
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

(s/defn find-id-by-original-id :- s/Str
  [msg bundle-result original-id]
  (let [{:keys [id error] :as result} (find-result-by-original-id bundle-result original-id)]
    (or id
        (throw (ex-info (str "Missing long id for transient "
                             original-id
                             (some->> error (str ": ")))
                        (cond-> {:msg msg
                                              :original-id original-id
                                              :bundle-result bundle-result}
                                       result (assoc :result result)))))))

(defn resolve-ids
  "Resolves transient IDs in the target_ref and the source_ref
  of a relationship"
  [bundle-result
   {:keys [source_ref target_ref] :as relationship}]
  (let [by-original-id (set/index (:results bundle-result)
                                  [:original_id])
        source-result (first
                       (get by-original-id
                            {:original_id source_ref}))
        target-result (first
                       (get by-original-id
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
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [{:keys [get-store]} (helpers/get-service-map app :StoreService)

           indicators [(mk-indicator 0)
                       (mk-indicator 1)]
           sightings [(mk-sighting 0)
                      (mk-sighting 1)]
           identity_assertions [(mk-identity-assertion 0)
                                (mk-identity-assertion 1)]
           relationships (map (fn [idx indicator sighting]
                                (mk-relationship idx
                                                 sighting
                                                 indicator
                                                 "sighting-of"))
                              (range)
                              indicators
                              sightings)]
       (testing "Import bundle with all entity types"
         (let [new-bundle (deep-dissoc-entity-ids bundle-maximal)
               response (POST app
                              "ctia/bundle/import"
                              :body new-bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (when (is (= 200 (:status response)))
             (is (= (count-bundle-entities new-bundle)
                    (count-bundle-result-entities (:results bundle-result)
                                                  "created"))
                 "All entities are created"))))
       (testing "Create"
         (let [bundle {:type "bundle"
                       :source "source"
                       :indicators (set indicators)
                       :sightings (set sightings)
                       :identity_assertions (set identity_assertions)
                       :relationships (set relationships)}
               response (POST app
                              "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (when (is (= 200 (:status response)))

             (is (every? #(= "created" %)
                         (map :result (:results bundle-result)))
                 "All entities are created")

             (doseq [entity (concat indicators
                                    sightings
                                    (map #(resolve-ids bundle-result %)
                                         relationships))]
               (validate-entity-record
                 app
                 (find-result-by-original-id bundle-result (:id entity))
                 entity)))))
       (testing "Update with partial"
         (let [updated-indicators (set (map with-modified-description indicators))
               bundle
               {:type "bundle"
                :source "source"
                ;; partial entity updates are allowed (:producer is a required Indicator entry)
                :indicators (into #{} (map #(select-keys % [:id :external_ids :description])) updated-indicators)
                :sightings (set (map with-modified-description sightings))
                :relationships (set (map with-modified-description relationships))}
               response (POST app
                              "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (when (is (= 200 (:status response)))
             (is (pos? (count (:results bundle-result))))
             (is (every? #(= "updated" %)
                         (map :result (:results bundle-result)))
                 "All existing entities are updated")
             (doseq [entity (concat updated-indicators
                                    (:sightings bundle)
                                    (map #(resolve-ids bundle-result %)
                                         (:relationships bundle)))]
               (validate-entity-record
                 app
                 (find-result-by-original-id bundle-result (:id entity))
                 entity)))))
       (testing "Update and create"
         (let [indicator (mk-indicator 2000)
               sighting (first sightings)
               relationship (mk-relationship 2000
                                             sighting
                                             indicator
                                             "sighting-of")
               bundle
               {:type "bundle"
                :source "source"
                :indicators [indicator]
                :sightings [sighting]
                :relationships [relationship]}
               response (POST app
                              "ctia/bundle/import"
                              :body bundle
                              :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result (:parsed-body response)]
           (when (is (= 200 (:status response)))

             (is (pos? (count (:results bundle-result))))

             (doseq [entity [indicator sighting
                             (resolve-ids bundle-result relationship)]]
               (validate-entity-record
                 app
                 (find-result-by-original-id bundle-result (:id entity))
                 entity)))))
       (testing "Bundle with missing entities"
         (let [relationship (mk-relationship 2001
                                             (first sightings)
                                             (mk-indicator 2001)
                                             "sighting-of")
               bundle {:type "bundle"
                       :source "source"
                       :relationships [relationship]}
               response-create (POST app
                                     "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-create (:parsed-body response-create)]
           (when (is (= 200 (:status response-create)))
             (is (= [{:original_id (:id relationship),
                      :result "error",
                      :type :relationship,
                      :error (str "A relationship cannot be created if a "
                                  "source or a target ref is still a transient "
                                  "ID (The source or target entity is probably "
                                  "not provided in the bundle)")}]
                    (:results bundle-result-create))
                 relationship))))
       (testing "Custom external prefix keys"
         (let [bundle {:type "bundle"
                       :source "source"
                       :indicators #{(assoc (first indicators)
                                            :external_ids
                                            ["custom-2"])}}
               response-create (POST app
                                     "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-create (:parsed-body response-create)
               response-update (POST app
                                     "ctia/bundle/import"
                                     :query-params {"external-key-prefixes" "custom-"}
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-update (:parsed-body response-update)]
           (when (is (= 200 (:status response-create)))
             (is (pos? (count (:results bundle-result-update))))
             (is (every? #(= "created" %)
                         (map :result (:results bundle-result-create)))
                 "All new entities are created"))

           (when (is (= 200 (:status response-update)))
             (is (pos? (count (:results bundle-result-create))))
             (is (every? #(= "updated" %)
                         (map :result (:results bundle-result-update)))
                 "All existing entities are updated"))))
       (testing "schema failures"
         (testing "Fail on creating partial entities"
           (let [partial-indicator (-> (first indicators)
                                       (assoc :external_ids ["custom-3"])
                                       (dissoc :producer))
                 bundle {:type "bundle"
                         :source "source"
                         :indicators #{partial-indicator}}
                 response-create (POST app
                                       "ctia/bundle/import"
                                       :query-params {"external-key-prefixes" "custom-"}
                                       :body bundle
                                       :headers {"Authorization" "45c1f5e3f05d0"})
                 bundle-result-create (:parsed-body response-create)]
             (when (is (= 200 (:status response-create)))
               (let [[{:keys [result error msg]}] (:results bundle-result-create)]
                 (is (= 1 (count (:results bundle-result-create))))
                 (is (= "error" result))
                 (is (= "Entity validation Error" error))
                 (is (str/ends-with? msg "- failed: (contains? % :producer) spec: :new-indicator/map\n"))))))
         (testing "Fail on patching bad partial entities"
           (let [bundle {:type "bundle"
                         :source "source"
                         :indicators #{(-> (first indicators)
                                           (assoc :external_ids ["custom-3"]))}}
                 response-create (POST app
                                       "ctia/bundle/import"
                                       :query-params {"external-key-prefixes" "custom-"}
                                       :body bundle
                                       :headers {"Authorization" "45c1f5e3f05d0"})
                 bundle-result-create (:parsed-body response-create)
                 response-update (POST app
                                       "ctia/bundle/import"
                                       :query-params {"external-key-prefixes" "custom-"}
                                       :body (update bundle :indicators #(into #{} (map (fn [e]
                                                                                          (assoc e :producer {:something :bad})))
                                                                               %))
                                       :headers {"Authorization" "45c1f5e3f05d0"})
               bundle-result-update (:parsed-body response-update)]
             (when (is (= 200 (:status response-create)))
               (is (pos? (count (:results bundle-result-create))))
               (is (every? #(= "created" %)
                           (map :result (:results bundle-result-create)))
                   "All new entities are created"))

             (when (is (= 400 (:status response-update)))
               (is (= {:errors {:indicators #{{:producer "(not (instance? java.lang.String {:something :bad}))"}}}}
                      bundle-result-update))))))
       (testing "Partial results with errors"
         (let [indicator-store-state (-> (get-store :indicator) :state)
               indexname (:index indicator-store-state)
               ;; close indicator index to produce ES errors on that store
               _ (es-index/close! (:conn indicator-store-state) indexname)]
           (try (let [bundle {:type "bundle"
                              :source "source"
                              :sightings [(mk-sighting 10)
                                          (mk-sighting 11)]
                              ;; Remove external_ids to avoid errors
                              ;; coming from the search operation
                              :indicators [(dissoc (mk-indicator 10)
                                                   :external_ids)]}
                      response-create (POST app
                                            "ctia/bundle/import"
                                            :body bundle
                                            :headers {"Authorization" "45c1f5e3f05d0"})
                      bundle-result-create (:parsed-body response-create)]
                  (when (is (= 200 (:status response-create)))
                    (is (every? #(= "created" %)
                                (->> (:results bundle-result-create)
                                     (filter #(= "sighting" %))
                                     (map :result)))
                        "All valid entities are created")
                    (doseq [entity (:sightings bundle)]
                      (validate-entity-record
                        app
                        (find-result-by-original-id bundle-result-create (:id entity))
                        entity))
                    (let [indicators (filter
                                       #(= :indicator (:type %))
                                       (:results bundle-result-create))]
                      (is (seq indicators)
                          "The result collection for indicators is not empty")
                      (is (every? #(contains? % :error) indicators)))))
                (finally
                  ;; reopen index to enable cleaning
                  (es-index/open! (:conn indicator-store-state) indexname)))))))))

(deftest ^:encoding bundle-import-non-utf-8-encoding
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [indicator (-> (mk-indicator 0)
                         (assoc :description "qй"))]
       (testing "Non-ASCII symbols imported with UTF-8 encoding"
         (let [bundle (assoc ctia.bundle.core/empty-bundle
                             :indicators #{indicator})
               import-response (POST app
                                     "ctia/bundle/import"
                                     :body bundle
                                     :headers {"Authorization" "45c1f5e3f05d0"})
               indicator-id (get-in import-response [:parsed-body :results 0 :id])
               export-response (GET app
                                    "ctia/bundle/export"
                                    :query-params {:ids [indicator-id]}
                                    :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 (:status import-response)))
           (is (= "qй" (-> export-response
                           (get-in [:parsed-body :indicators])
                           (first)
                           (get :description))))))))))

(deftest bundle-import-should-not-allow-disabled-entities
  (testing "Attempts to import bundle with disabled entities should fail"
    (let [disable [:asset :asset-properties :actor :sighting]]
      (helpers/with-config-transformer
        #(assoc-in
          % [:ctia :features :disable]
          (->> disable (map name) (str/join ",")))
        (test-for-each-store-with-app
         (fn [app]
           (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
           (whoami-helpers/set-whoami-response app
                                               "45c1f5e3f05d0"
                                               "foouser"
                                               "foogroup"
                                               "user")
           ;; moved inside a context with `app` so APIHandlerServices can be stubbed
           (testing "disabled entities should be removed from Bundle schema"
             (let [selected-keys    #{:asset :target-record :asset-properties}
                   set-of           (fn [model] (set (repeat 3 model)))
                   fake-bundle      {:id                    "http://ex.tld/ctia/bundle/bundle-5023697b-3857-4652-9b53-ccda297f9c3e"
                                     :source                "source"
                                     :type                  "bundle"
                                     :assets                (set-of asset-maximal)
                                     :asset_refs            #{"http://ex.tld/ctia/asset/asset-5023697b-3857-4652-9b53-ccda297f9c3e"}
                                     :asset_properties      (set-of asset-properties-maximal)
                                     :asset_properties_refs #{"http://ex.tld/ctia/asset-properties/asset-properties-5023697b-3857-4652-9b53-ccda297f9c3e"}
                                     :target_record_refs    #{"http://ex.tld/ctia/target-record/target-record-5023697b-3857-4652-9b53-ccda297f9c3e"}
                                     :target_records        (set-of target-record-maximal)}
                   api-handler-svcs (assoc-in (app->APIHandlerServices app) [:FeaturesService :entity-enabled?] #(contains? selected-keys %))]
               (is (map? (s/validate (core/prep-bundle-schema api-handler-svcs) fake-bundle)))
               (is (thrown? clojure.lang.ExceptionInfo
                            (s/validate
                              (core/prep-bundle-schema api-handler-svcs)
                              (assoc fake-bundle :incidents (set-of incident-maximal))))
                   "Bundle schema with a key that's not explicitly allowed shouldn't validate")))
           (let [new-bundle               (deep-dissoc-entity-ids bundle-maximal)
                 resp                     (POST app "ctia/bundle/import"
                                            :body new-bundle
                                            :headers {"Authorization" "45c1f5e3f05d0"})
                 disallowed-keys-expected (->> disable
                                               (mapcat core/entity->bundle-keys)
                                               set)
                 disallowed-keys-res      (->> resp
                                               :body
                                               edn/read-string
                                               :errors
                                               (filter (fn [[_ v]] (= v 'disallowed-key)))
                                               (map first)
                                               set)]
             (is (= disallowed-keys-expected disallowed-keys-res)))))))))

(deftest bundle-import-errors-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [bundle {:source "foo"
                   :indicators [(assoc (mk-indicator 1)
                                       :valid_time
                                       {:start_time #inst "2042-05-11T00:40:48.212-00:00"
                                        :end_time #inst "4242-07-11T00:40:48.212-00:00"})]}
           response-create (POST app
                                 "ctia/bundle/import"
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
  (client-id [_] nil)
  (allowed-capabilities [_] #{})
  (capable? [_ _] true)
  (rate-limit-fn [_ _] false))

(deftest all-pages-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [{{:keys [get-store]} :StoreService} (app/service-graph app)

           duplicated-indicators (->> (mk-indicator 0)
                                      (repeat (* 10 core/find-by-external-ids-limit))
                                      (map #(assoc % :id (id/make-transient-id nil))))
           more-indicators (map mk-indicator (range 1 10))
           all-indicators (into (set duplicated-indicators) more-indicators)
           duplicated-external-id (-> duplicated-indicators first :external_ids first)
           all-external-ids (mapcat :external_ids all-indicators)
           bundle {:type "bundle"
                   :source "source"
                   :indicators all-indicators}
           response-create (POST app
                                 "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
           ident (FakeIdentity. "foouser" ["foogroup"])
           matched-entities (core/all-pages :indicator all-external-ids ident get-store)
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
              (into #{} (mapcat :external_ids) matched-entities))
           "all-pages must match at least one entity for each existing external-id")))))

(deftest find-by-external-ids-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [;; See fixture-find-by-external-ids-limit
           nb-entities (+ core/find-by-external-ids-limit 5)
           bundle {:type "bundle"
                   :source "source"
                   :indicators (set (map mk-indicator (range nb-entities)))}
           do-import #(POST app
                            "ctia/bundle/import"
                            :body bundle
                            :headers {"Authorization" "45c1f5e3f05d0"})
           response-create (do-import)
           bundle-result-create (:parsed-body response-create)
           response-update (do-import)
           bundle-result-update (:parsed-body response-update)]
       (is (= 200 (:status response-create)))
       (is (= 200 (:status response-update)))
       (is (= nb-entities
              (count (:results bundle-result-create)))
           bundle-result-create)
       (is (= nb-entities
              (count (:results bundle-result-update))))

       (is (every? #(= "created" %)
                   (map :result (:results bundle-result-create)))
           "All new entities are created")
       (is (every? #(= "updated" %)
                   (map :result (:results bundle-result-update)))
           "All existing entities are updated")))))

(def bundle-fixture-1
  (let [indicators [(mk-indicator 0)
                    (mk-indicator 1)]
        sightings [(mk-sighting 0)]
        relationships (map (fn [idx indicator]
                             (mk-relationship idx
                                              (first sightings)
                                              indicator
                                              "sighting-of"))
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
                             (mk-relationship idx
                                              (first sightings)
                                              indicator
                                              "sighting-of"))
                           (range 100 1000)
                           indicators)]
    {:type "bundle"
     :source "source"
     :indicators (set indicators)
     :sightings (set sightings)
     :relationships (set relationships)}))

(deftest bundle-export-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "filtering on entities ids"
       (let [post-bundle
             (fn [bundle-fixture]
               (POST app
                     "/ctia/bundle/import"
                     :body bundle-fixture
                     :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-res-1 (post-bundle bundle-fixture-1)
             bundle-res-2 (post-bundle bundle-fixture-2)
             first-sighting-id (fn [bundle-res]
                                 (some->> bundle-res
                                          :parsed-body
                                          :results
                                          (group-by :type)
                                          :sighting
                                          first
                                          :id))
             sighting-id-1 (first-sighting-id bundle-res-1)
             sighting-id-2 (first-sighting-id bundle-res-2)
             bundle-get-res-1
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids sighting-id-1}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-2
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids sighting-id-2}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-3
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-4
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]
                                  :include_related_entities false}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-get-res-5
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1
                                        sighting-id-2]
                                  :related_to ["target_ref" "source_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-post-res-empty-ids
             (:parsed-body
              (POST app
                   "ctia/bundle/export"
                   :body {:ids nil}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-post-res
             (:parsed-body
              (POST app
                    "ctia/bundle/export"
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
             "POST and GET bundle/export routes must return the same results")
         (is (= {:type "bundle"
                 :source "ctia"} bundle-post-res-empty-ids)
             "POST bundle/export with an empty ids list should return an empty bundle"))))))

(deftest bundle-export-with-unreachable-entities
  (testing "external and deleted entities in fetched relationships should be ignored"
    (test-for-each-store-with-app
     (fn [app]
       (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
       (whoami-helpers/set-whoami-response app
                                           "45c1f5e3f05d0"
                                           "foouser"
                                           "foogroup"
                                           "user")
       (let [;; set :tlp "red" to make `sighting-1` inaccessible to anyone except owner
             sighting-1 (assoc (mk-sighting 1) :tlp "red")
             indicator-1 (mk-indicator 1)
             indicator-2 (mk-indicator 2)
             relationship-1 (mk-relationship 1 sighting-1 indicator-1 "sighting-of")
             relationship-2 (mk-relationship 2 sighting-1 indicator-2 "sighting-of")
             relationship-3 ;;relationship to external entity
             {:type "relationship"
              :source_ref (:id sighting-1)
              :relationship_type "sighting-of"
              :target_ref "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"}
             bundle {:type "bundle"
                     :source "source"
                     :sightings #{sighting-1}
                     :indicators #{indicator-1 indicator-2}
                     :relationships #{relationship-1 relationship-2 relationship-3}}
             bundle-import (POST app
                                 "ctia/bundle/import"
                                 :body bundle
                                 :headers {"Authorization" "45c1f5e3f05d0"})
             id-map (some->> bundle-import
                             :parsed-body
                             :results
                             (map (fn [{:keys [original_id id]}]
                                    [original_id id]))
                             (into {}))
             indicator-1-final-id (get id-map (:id indicator-1))
             indicator-2-final-id (get id-map (:id indicator-2))
             sighting-1-final-id (get id-map (:id sighting-1))
             delete-indicator (DELETE app
                                      (format "ctia/indicator/%s" (id/str->short-id indicator-1-final-id))
                                      :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-export (GET app
                                "ctia/bundle/export"
                                :query-params {:ids [sighting-1-final-id]}
                                :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-export-body (:parsed-body bundle-export)]
         (is (= 200 (:status bundle-import)) "Import request status should be 200")
         (is (= 200 (:status bundle-export)) "Export request status should be 200")
         (is (= 204 (:status delete-indicator)) "Delete indicator request status should be 204")
         (is (= (list indicator-2-final-id) (->> (:indicators bundle-export-body)
                                                 (map :id))))
         (is (= 3 (count (:relationships bundle-export-body))))
         (is (= 1 (count (:sightings bundle-export-body))))

         (testing "unauthorized access"
           ;; Create another user and try to access sighting with :tlp "red"
           (helpers/set-capabilities! app "baruser" ["bargroup"] "user" (all-capabilities))
           (whoami-helpers/set-whoami-response app
                                               "45c1f5e3f05d1"
                                               "baruser"
                                               "bargroup"
                                               "user")
           (let [bundle-export (GET app
                                    "ctia/bundle/export"
                                    :query-params {:ids [sighting-1-final-id]}
                                    :headers {"Authorization" "45c1f5e3f05d1"})]
             (is (= core/empty-bundle (:parsed-body bundle-export))))))))))

(def bundle-graph-fixture
  (let [indicator-1 (mk-indicator 1)
        indicator-2 (mk-indicator 2)
        indicator-3 (mk-indicator 3)
        sighting-1 (mk-sighting 1)
        sighting-2 (mk-sighting 2)
        incident-1 (mk-incident 1)
        incident-2 (mk-incident 2)
        relationship-1 (mk-relationship 1 sighting-1 indicator-1 "sighting-of")
        relationship-2 (mk-relationship 2 sighting-1 indicator-2 "sighting-of")
        relationship-3 (mk-relationship 3 sighting-2 indicator-1 "sighting-of")
        relationship-4 (mk-relationship 4 sighting-2 indicator-3 "sighting-of")
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
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "testing max number of retrieved relationships"
       (let [imported-bundle (POST app
                                   "ctia/bundle/import"
                                   :body fixture-many-relationships
                                   :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-ids (->> (-> imported-bundle :parsed-body :results)
                               (filter #(= (:type %) :sighting))
                               (map :id))
             exported-bundle
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids sighting-ids}
                   :headers {"Authorization" "45c1f5e3f05d0"}))]
         (is (= 500 (count (:relationships exported-bundle)))))))))

(deftest bundle-export-graph-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "testing relationships filters"
       (let [bundle-res
             (:parsed-body (POST app
                                 "ctia/bundle/import"
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
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-1]
                                  :related_to ["source_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-from-target-1
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [indicator-id-1]
                                  :related_to ["target_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))
             bundle-from-target-2
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [indicator-id-2]
                                  :related_to ["target_ref"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             ;; node type filters
             bundle-sighting-source
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [incident-id-2]
                                  :source_type ["sighting"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-sighting+indicator-source
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [incident-id-2]
                                  :source_type ["sighting" "indicator"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-incident-target-get
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-2]
                                  :target_type "incident"}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-incident+indicator-target-get
             (:parsed-body
              (GET app
                   "ctia/bundle/export"
                   :query-params {:ids [sighting-id-2]
                                  :target_type ["incident" "indicator"]}
                   :headers {"Authorization" "45c1f5e3f05d0"}))

             bundle-incident-target-post
             (:parsed-body
              (POST app
                    "ctia/bundle/export"
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

           (is (seq (:indicators bundle-sighting+indicator-source)))
           (is (seq (:sightings bundle-sighting+indicator-source)))

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
           (is (= bundle-incident-target-get bundle-incident-target-post))

           (is (seq (:incidents  bundle-incident+indicator-target-get)))
           (is (seq (:indicators  bundle-incident+indicator-target-get)))))))))

(defn with-tlp-property-setting [tlp f]
  (helpers/with-config-transformer*
    #(-> %
         (assoc-in [:ctia :access-control :min-tlp] tlp)
         (assoc-in [:ctia :access-control :default-tlp] tlp))
    f))

(deftest bundle-tlp-test
 (with-tlp-property-setting "amber"
  (fn []
   (test-for-each-store-with-app
    (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "entities TLP settings are validated"
       (let [sighting (assoc (mk-sighting 1) :tlp "white")
             new-bundle
             {:type "bundle"
              :source "source"
              :sightings #{sighting}}

             res (POST app
                       "ctia/bundle/import"
                       :body new-bundle
                       :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= "Entity Access Control validation Error" (-> (:parsed-body res) :results first :error))
             res)
         (is (= 200 (:status res))))))))))

(deftest bundle-acl-fields-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "Bundle export allows acl fields"
       (let [sighting (assoc (mk-sighting 1) :authorized_users ["foo"])
             judgement (assoc (mk-judgement) :authorized_users ["foo"])
             judgement-post-res (POST app
                                      "ctia/judgement"
                                      :body judgement
                                      :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-post-res (POST app
                                     "ctia/sighting"
                                     :body sighting
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             sighting-id (-> sighting-post-res :parsed-body :id)
             judgement-id (-> judgement-post-res :parsed-body :id)
             bundle-get-res (GET app
                                 "ctia/bundle/export"
                                 :query-params {:ids [sighting-id
                                                      judgement-id]}
                                 :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-post-res (POST app
                                   "ctia/bundle/export"
                                   :body {:ids [sighting-id
                                                judgement-id]}
                                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 200 (:status bundle-post-res)))
         (is (= 200 (:status bundle-get-res))))))))

(deftest bundle-export-casebook-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "Bundle export should include casebooks"
       (let [casebook (mk-casebook)
             incident (mk-incident 1)
             casebook-post-res (POST app
                                     "ctia/casebook"
                                     :body casebook
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             incident-post-res (POST app
                                     "ctia/incident"
                                     :body incident
                                     :headers {"Authorization" "45c1f5e3f05d0"})
             incident-id (-> incident-post-res :parsed-body :id)
             casebook-id (-> casebook-post-res :parsed-body :id)
             incident-short-id (id/str->short-id incident-id)
             link-res (POST app
                            (str "/ctia/incident/" incident-short-id "/link")
                            :body {:casebook_id casebook-id
                                   :tlp "white"}
                            :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-get-res (GET app
                                 "ctia/bundle/export"
                                 :query-params {:ids [incident-id]}
                                 :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 (:status casebook-post-res)))
         (is (= 201 (:status incident-post-res)))
         (is (= 201 (:status link-res)))
         (is (= 200 (:status bundle-get-res)))
         (is (seq (-> bundle-get-res :parsed-body :casebooks))))))))

(deftest ^:disabled race-condition-test
  (helpers/with-properties
    ["ctia.store.es.default.refresh" "false"
     "ctia.store.es.default.refresh_interval" "1s"
     "ctia.store.bulk-refresh" "false"
     "ctia.store.bundle-refresh" "wait_for"]
    (helpers/fixture-ctia-with-app
     (fn [app]
       (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
       (whoami-helpers/set-whoami-response app
                                           "45c1f5e3f05d0"
                                           "foouser"
                                           "foogroup"
                                           "user")
       (let [new-bundle {:source "anonymous coward"
                         :incidents
                         [{:source "CTR-WIN-IP"
                           :external_ids
                           ["secure-endpoint-incident-4050710c4bb9692e55465c32ae150730a57936cc95377f09a76952ead9556da0"]
                           :title "CTR-WIN-IP in group Protect @ 20211124 13:52:21"
                           :incident_time {:opened "2021-11-24T13:52:21.000Z"}
                           :source_uri
                           "https://console.qa1.immunet.com/computers/74eff879-a30b-4a39-8d53-3501a2155f84/trajectory2"
                           :status "New"
                           :severity "High"
                           :tlp "amber"
                           :timestamp "2021-11-24T13:52:23.906Z"
                           :confidence "High"}]}
             import (fn [new-bundle]
                      (POST app
                            "ctia/bundle/import"
                            :body new-bundle
                            :headers {"Authorization" "45c1f5e3f05d0"}))
             res (->> (pmap import (repeat 10 new-bundle))
                      (mapcat (comp :results :parsed-body)))]
         (testing "there is a race condition for checking external ids"
           (is (< 1 (count (filter #(= "created" (:result %)) res))))))))))

(deftest bundle-asset-relationships-test
  (test-for-each-store-with-app
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
      (whoami-helpers/set-whoami-response app
                                          "45c1f5e3f05d0"
                                          "foouser"
                                          "foogroup"
                                          "user")

      (let [[oldv1 oldv2 newv1 newv2 newv3] (repeatedly (comp str gensym))
            [relationship1-original-id
             asset_mapping1-original-id
             asset_property1-original-id
             asset1-original-id
             asset2-original-id] (repeatedly #(str "transient:" (random-uuid)))
            asset1 {:asset_type "device"
                    :valid_time {:start_time #inst "2023-03-02T19:14:46.658-00:00"}
                    :schema_version "1.0.19"
                    :type "asset"
                    :source "something"
                    :external_ids ["asset-1"]
                    :title "something"
                    :source_uri "https://something"
                    :id asset1-original-id
                    :timestamp #inst "2023-03-02T19:14:46.658-00:00"}
            asset_mapping1 {:asset_type "device"
                            :valid_time {:start_time #inst "2023-03-02T19:14:46.660-00:00"}
                            :stability "Managed"
                            :schema_version ctim-schema-version
                            :observable {:value "something" :type "hostname"}
                            :asset_ref asset1-original-id
                            :type "asset-mapping"
                            :source "Something"
                            :source_uri "https://something"
                            :id asset_mapping1-original-id
                            :timestamp #inst "2023-03-02T19:14:46.660-00:00"
                            :specificity "Unique"
                            :confidence "Unknown"}
            asset_property1 {:properties [{:name "something1" :value oldv1}
                                          {:name "something2" :value oldv2}]
                             :valid_time {:start_time #inst "2023-03-02T19:14:46.660-00:00"}
                             :schema_version ctim-schema-version
                             :asset_ref asset1-original-id
                             :type "asset-properties"
                             :source "something"
                             :source_uri "https://something"
                             :id asset_property1-original-id
                             :timestamp #inst "2023-03-02T19:14:46.783-00:00"}
            new-bundle (-> bundle-minimal
                           (assoc :assets #{asset1}
                                  :asset_mappings #{asset_mapping1}
                                  :asset_properties #{asset_property1}
                                  :relationships #{{:id relationship1-original-id
                                                    :source_ref "https://private.intel.int.iroh.site:443/ctia/incident/incident-4fb91401-36a5-46d1-b0aa-01af02f00a7a"
                                                    :target_ref asset_mapping1-original-id, :relationship_type "related-to", :source "IROH Risk Score Service"}
                                                   {:source_ref "https://private.intel.int.iroh.site:443/ctia/incident/incident-4fb91401-36a5-46d1-b0aa-01af02f00a7a"
                                                    :target_ref asset_property1-original-id, :relationship_type "related-to", :source "IROH Risk Score Service"}
                                                   {:source_ref "https://private.intel.int.iroh.site:443/ctia/incident/incident-4fb91401-36a5-46d1-b0aa-01af02f00a7a"
                                                    :target_ref asset1-original-id, :relationship_type "related-to", :source "IROH Risk Score Service"}}))
            create-response (POST app
                                  "ctia/bundle/import"
                                  :body new-bundle
                                  :headers {"Authorization" "45c1f5e3f05d0"})
            {create-results :results :as create-bundle-results} (:parsed-body create-response)
            ;; resolve in order of creation/patch for easier debugging
            asset1-id (find-id-by-original-id :asset1-id create-bundle-results asset1-original-id)
            asset_property1-id (find-id-by-original-id :asset_property1-id create-bundle-results asset_property1-original-id)
            asset_mapping1-id (find-id-by-original-id :asset_mapping1-id create-bundle-results asset_mapping1-original-id)
            relationship1-id (find-id-by-original-id :relationship1-id create-bundle-results relationship1-original-id)]
        (testing "relationships are created for asset mappings/properties"
          (when (is (= 200 (:status create-response)))
            (is (= 6 (count create-results)))
            (is (every? (comp #{"created"} :result) create-results)
                (pr-str (mapv :result create-results)))))
        (testing "asset-properties and asset-mappings are merged with old-entity when patched"
          (let [updated-asset_property1 (-> asset_property1
                                            (select-keys [:id :asset_ref :type])
                                            (assoc :properties [{:name "something1" :value newv1}
                                                                {:name "something-else" :value newv2}]))
                updated-asset_mapping1 asset_mapping1
                update-bundle (-> bundle-minimal
                                  (assoc :assets #{asset1}
                                         :asset_mappings #{updated-asset_mapping1}
                                         :asset_properties #{updated-asset_property1}))
                update-response (POST app
                                      "ctia/bundle/import"
                                      :body update-bundle
                                      :headers {"Authorization" "45c1f5e3f05d0"})
                {update-results :results :as update-bundle-result} (:parsed-body update-response)]
            (when (is (= 200 (:status update-response)))
              (is (= 3 (count update-results)) update-results)
              (is (every? (comp #{"updated"} :result) update-results)
                  (pr-str (mapv :result update-results)))
              (let [get-stored (fn [entity]
                                 (let [realized-id (some (fn [{:keys [original_id id]}]
                                                           (when (= original_id (:id entity))
                                                             id))
                                                         update-results)
                                       _ (assert realized-id [(:id entity) update-results])
                                       response (GET app
                                                     (format "ctia/%s/%s"
                                                             (name (:type entity))
                                                             (encode realized-id))
                                                     :headers {"Authorization" "45c1f5e3f05d0"})]
                                   (:parsed-body response)))]
                (testing ":asset_mappings"
                  (let [stored (get-stored updated-asset_mapping1)]
                    ;; no interesting merging logic on asset mappings
                    (is (= (dissoc stored :id :schema_version :asset_ref :owner :groups :timestamp)
                           (-> updated-asset_mapping1
                               (dissoc :id :schema_version :asset_ref :timestamp)
                               (assoc :tlp "green")
                               (assoc-in [:valid_time :end_time] #inst "2525-01-01T00:00:00.000-00:00"))))))
                (testing ":asset_properties"
                  (let [stored (get-stored updated-asset_property1)]
                    (testing ":properties are merged, newer wins"
                      (is (= [{:name "something-else" :value newv2}
                              {:name "something1" :value newv1}
                              {:name "something2" :value oldv2}]
                             (:properties stored))))
                    (is (= #inst "2525-01-01T00:00:00.000-00:00"
                           (get-in stored [:valid_time :end_time])))))))))
        (testing "patched relationships, asset mappings/properties resolve refs after creating other entities"
          (let [;; on existing entities, patch asset_ref to a newly created asset,
                ;; and :{source/target}_ref to newly created entities
                asset2-external-id (str "asset-2-" (random-uuid))
                asset2 (assoc asset1 :id asset2-original-id :external_ids [asset2-external-id])
                updated-asset_property1 {:id asset_property1-id
                                         :asset_ref asset2-original-id} 
                updated-asset_mapping1 {:id asset_mapping1-id
                                        :asset_ref asset2-original-id} 
                updated-relationship1 {:id relationship1-id
                                       :source_ref asset2-original-id
                                       :target_ref asset2-original-id}
                create+update-bundle (-> bundle-minimal
                                         (assoc :assets #{asset2}
                                                :asset_properties #{updated-asset_property1}
                                                :asset_mappings #{updated-asset_mapping1}
                                                :relationships #{updated-relationship1}))
                create+update-response (POST app
                                             "ctia/bundle/import"
                                             :body create+update-bundle
                                             :headers {"Authorization" "45c1f5e3f05d0"})
                {create+update-results :results :as create+update-bundle-result} (:parsed-body create+update-response)]
            (when (is (= 200 (:status create+update-response)))
              (let [asset2-id (find-id-by-original-id :asset2-id create+update-bundle-result asset2-original-id)]
                (is (= 4 (count create+update-results)))
                (is (= #{{:id asset_mapping1-id
                          :result "updated"
                          :type :asset-mapping}
                         {:id asset_property1-id
                          :result "updated"
                          :type :asset-properties}
                         {:id asset2-id
                          :original_id asset2-original-id
                          :result "created"
                          :type :asset
                          :external_ids [asset2-external-id]}
                         {:id relationship1-id
                          :result "updated"
                          :type :relationship}}
                       (set create+update-results)))))))))))
