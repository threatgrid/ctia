(ns ctia.bundle.core-asset-ref-entities-test
  "Additonal tests for entities with AssetRef field.

  For Assets, (unlike other entities) it was decided not to use Relationship
  objects to define association with other entities. Specifically, AssetMap and
  AssetProperties are linked to Assets via their :asset-ref fields.

  These tests are to ensure that such a relationship is observed when these
  types of objects when they created via Bundle Import"
  (:require
   [clojure.test :refer [deftest is are testing use-fixtures join-fixtures]]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.core :as bundle]
   [ctia.store :as store]
   [ctia.test-helpers.auth :as auth]
   [ctia.test-helpers.core :as th]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [puppetlabs.trapperkeeper.app :as app]))

(def bundle-ents
  "Sample Bundle Map for testing."
  {:asset_mappings
   #{{:asset_ref           "transient:asset-1"
      :asset_type          "device"
      :confidence          "High"
      :external_ids        ["d2dcbd00e9bb49719d3fa0a59b1ddfdf"]
      :external_references [{:description "Description text"
                             :external_id "T1061"
                             :hashes      ["#section1"]
                             :source_name "source"
                             :url         "https://ex.tld/wiki/T1061"}]
      :language            "language"
      :observable          {:type "email" :value "tester@test.com"}
      :revision            1
      :schema_version      "1.0"
      :source              "cisco:unified_connect"
      :source_uri          "http://example.com/asset-mapping-2"
      :specificity         "Unique"
      :stability           "Managed"
      :timestamp           #inst "2016-02-11T00:40:48.000-00:00"
      :tlp                 "green"
      :type                "asset-mapping"
      :valid_time          {:end_time   #inst "2525-01-01T00:00:00.000-00:00"
                            :start_time #inst "2020-01-11T00:40:48.000-00:00"}}
     {:asset_ref           "transient:asset-1"
      :asset_type          "device"
      :confidence          "High"
      :external_ids        ["d2dcbd00e9bb49719d3fa0a59b1ddfdf"]
      :external_references [{:description "Description text"
                             :external_id "T1061"
                             :hashes      ["#section1"]
                             :source_name "source"
                             :url         "https://ex.tld/wiki/T1061"}]
      :language            "language"
      :observable          {:type "ip" :value "100.213.110.122"}
      :revision            1
      :schema_version      "1.0"
      :source              "cisco:unified_connect"
      :source_uri          "http://example.com/asset-mapping-1"
      :specificity         "Unique"
      :stability           "Managed"
      :timestamp           #inst "2016-02-11T00:40:48.000-00:00"
      :tlp                 "green"
      :type                "asset-mapping"
      :valid_time          {:end_time   #inst "2525-01-01T00:00:00.000-00:00"
                            :start_time #inst "2020-01-11T00:40:48.000-00:00"}}}

   :asset_properties
   #{{:properties          [{:name "cisco:securex:posture:score", :value "23"}
                            {:name "asus:router:model", :value "RT-AC68U"}],
      :valid_time          {:start_time #inst "2020-01-11T00:40:48.000-00:00",
                            :end_time   #inst "2525-01-01T00:00:00.000-00:00"},
      :schema_version      "1.0",
      :revision            1,
      :asset_ref           "transient:asset-1",
      :type                "asset-properties",
      :source              "cisco:unified_connect",
      :external_ids        ["29a4b476-a187-4160-8b36-81f7a0dbf137"],
      :external_references [{:source_name "source",
                             :external_id "T1061",
                             :url         "https://ex.tld/wiki/T1061",
                             :hashes      ["#section1"],
                             :description "Description text"}],
      :source_uri          "http://example.com/asset-properties",
      :language            "language",
      :tlp                 "green",
      :timestamp           #inst "2016-02-11T00:40:48.000-00:00"}}

   :assets
   #{{:asset_type          "device"
      :description         "asus router"
      :external_ids        ["61884b14e2734930a5ffdcce69207724"]
      :external_references [{:description "doesn't matter"
                             :external_id "T1061"
                             :hashes      ["#section1"]
                             :source_name "source"
                             :url         "https://ex.tld/wiki/T1061"}]
      :id                  "transient:asset-1"
      :language            "EN"
      :revision            1
      :schema_version      "1.0"
      :short_description   "awesome router"
      :source              "source"
      :source_uri          "http://example.com/asset/asus-router-1"
      :timestamp           #inst "2020-02-11T00:40:48.000-00:00"
      :title               "ASUS-ROUTER"
      :tlp                 "green"
      :type                "asset"
      :valid_time          {:end_time   #inst "2525-01-01T00:00:00.000-00:00"
                            :start_time #inst "2020-01-11T00:40:48.000-00:00"}}}})

(deftest bulk-for-asset-related-entities
  (testing "delay creation of :asset-mapping and :asset-properties, until all
  transient IDs for :asset are resolved"
    (th/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             login (map->Identity {:login  "foouser"
                                   :groups ["foogroup"]})
             bundle-import-data (bundle/prepare-import bundle-ents nil login services)
             bulk (bundle/prepare-bulk bundle-import-data)
             tempids (->> bundle-import-data
                          (map (fn [[_ entities-import-data]]
                                 (bundle/entities-import-data->tempids entities-import-data)))
                          (apply merge {}))]
         (testing "Bundle import initially, should skip the creation of
                   AssetMappings and AssetProperties objects"
          (with-redefs [bulk/gen-bulk-from-fn
                        (fn [_ bulk _ _ _ _]
                          (is (empty?
                               (select-keys
                                bulk [:asset_mappings
                                      :asset_properties])))
                          ;; Doesn't make sence to continue from here; once we
                          ;; asserted that asset-mapping and asset-properties
                          ;; won't be explicitly created (until we create Assets
                          ;; with non-transient IDs), we achieved the goal of
                          ;; this test.
                          (throw (Exception. "stopped intentionally")))]
            (is
             (thrown-with-msg?
              Exception #"stopped intentionally"
              (bulk/create-bulk
               bulk
               tempids
               login
               {}
               services))))))))))

(use-fixtures :once
  (join-fixtures
   [whoami-helpers/fixture-server]))

(deftest asset-refs-test
  (th/fixture-ctia-with-app
   (fn [app]
     (th/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [services (app/service-graph app)

           {{:keys [get-store]} :StoreService} services

           login              (map->Identity {:login  "foouser"
                                              :groups ["foogroup"]})
           bundle-import-data (bundle/prepare-import bundle-ents nil login services)
           bulk               (bundle/prepare-bulk bundle-import-data)
           tempids            (->> bundle-import-data
                                   (map (fn [[_ entities-import-data]]
                                          (bundle/entities-import-data->tempids
                                           entities-import-data)))
                                   (apply merge {}))
           {:keys [tempids]}  (bulk/create-bulk
                               bulk
                               tempids
                               login
                               {:refresh true}
                               services)
           get-records        (fn [store]
                                (-> (get-store store)
                                    (store/list-records
                                     {:query "*"}
                                     {:login  "foouser"
                                      :groups ["foogroup"]} {})
                                    :data))
           expected-asset-ref (get tempids (-> bundle-ents :assets first :id))]
       (testing "every AssetMapping and AssetProperties have proper
                 non-transient :asset-ref"
        (are [store] (every?
                      (partial = expected-asset-ref)
                      (->> store get-records (map :asset_ref)))
          :asset-mapping
          :asset-properties))))))
