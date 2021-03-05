(ns ctia.bundle.bundle-debug-test
  (:require
   [clojure.test :refer [deftest is are testing]]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.core :as bundle]
   [ctia.domain.entities :as ent]
   [ctia.flows.crud :as flows]
   [ctia.store :as store]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as th]
   [hashp.core]
   [puppetlabs.trapperkeeper.app :as app]))

(def bundle-ents
  {
   ;; :asset_mappings   #{{:asset_ref           "transient:asset-1"
   ;;                      :asset_type          "device"
   ;;                      :confidence          "High"
   ;;                      :external_ids        ["d2dcbd00e9bb49719d3fa0a59b1ddfdf"]
   ;;                      :external_references [{:description "Description text"
   ;;                                             :external_id "T1061"
   ;;                                             :hashes      ["#section1"]
   ;;                                             :source_name "source"
   ;;                                             :url         "https://ex.tld/wiki/T1061"}]
   ;;                      :language            "language"
   ;;                      :observable          {:type "email" :value "tester@test.com"}
   ;;                      :revision            1
   ;;                      :schema_version      "1.0"
   ;;                      :source              "cisco:unified_connect"
   ;;                      :source_uri          "http://example.com/asset-mapping-2"
   ;;                      :specificity         "Unique"
   ;;                      :stability           "Managed"
   ;;                      :timestamp           #inst "2016-02-11T00:40:48.000-00:00"
   ;;                      :tlp                 "green"
   ;;                      :type                "asset-mapping"
   ;;                      :valid_time          {:end_time   #inst "2525-01-01T00:00:00.000-00:00"
   ;;                                            :start_time #inst "2020-01-11T00:40:48.000-00:00"}}
   ;;                     {:asset_ref           "transient:asset-1"
   ;;                      :asset_type          "device"
   ;;                      :confidence          "High"
   ;;                      :external_ids        ["d2dcbd00e9bb49719d3fa0a59b1ddfdf"]
   ;;                      :external_references [{:description "Description text"
   ;;                                             :external_id "T1061"
   ;;                                             :hashes      ["#section1"]
   ;;                                             :source_name "source"
   ;;                                             :url         "https://ex.tld/wiki/T1061"}]
   ;;                      :language            "language"
   ;;                      :observable          {:type "ip" :value "100.213.110.122"}
   ;;                      :revision            1
   ;;                      :schema_version      "1.0"
   ;;                      :source              "cisco:unified_connect"
   ;;                      :source_uri          "http://example.com/asset-mapping-1"
   ;;                      :specificity         "Unique"
   ;;                      :stability           "Managed"
   ;;                      :timestamp           #inst "2016-02-11T00:40:48.000-00:00"
   ;;                      :tlp                 "green"
   ;;                      :type                "asset-mapping"
   ;;                      :valid_time          {:end_time   #inst "2525-01-01T00:00:00.000-00:00"
   ;;                                            :start_time #inst "2020-01-11T00:40:48.000-00:00"}}}
   ;; :asset_properties #{{:properties          [{:name "cisco:securex:posture:score", :value "23"}
   ;;                                            {:name "asus:router:model", :value "RT-AC68U"}],
   ;;                      :valid_time          {:start_time "2020-01-11T00:40:48Z",
   ;;                                            :end_time   "2525-01-01T00:00:00Z"},
   ;;                      :schema_version      "1.0",
   ;;                      :revision            1,
   ;;                      :asset_ref           "transient:asset-1",
   ;;                      :type                "asset-properties",
   ;;                      :source              "cisco:unified_connect",
   ;;                      :external_ids        ["29a4b476-a187-4160-8b36-81f7a0dbf137"],
   ;;                      :external_references [{:source_name "source",
   ;;                                             :external_id "T1061",
   ;;                                             :url         "https://ex.tld/wiki/T1061",
   ;;                                             :hashes      ["#section1"],
   ;;                                             :description "Description text"}],
   ;;                      :source_uri          "http://example.com/asset-properties",
   ;;                      :language            "language",
   ;;                      :tlp                 "green",
   ;;                      :timestamp           "2016-02-11T00:40:48Z"}}
   :assets           #{{:asset_type          "device"
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
(deftest bulk-test
  (th/fixture-ctia-with-app
   (fn [app]
     ;; (th/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (let [services (app/service-graph app)
           {{:keys [get-store all-stores]} :StoreService} services
           login  #ctia.auth.allow_all.Identity {}
           bundle-import-data (bundle/prepare-import bundle-ents nil login services)
           bulk               (bundle/prepare-bulk bundle-import-data)
           tempids            (->> bundle-import-data
                                   (map (fn [[_ entities-import-data]]
                                          (bundle/entities-import-data->tempids entities-import-data)))
                                   (apply merge {}))
           identity-map {:authorized-anonymous true}
           {:keys [tempids]} (bulk/create-bulk
                              bulk
                              tempids
                              login
                              {}
                              services)
           ;; short-ids (->> tempids vals (map (comp :short-id ent/long-id->id)))
           ]


       ;; (-> (get-store :asset) :state :index)

       (clojure.pprint/pprint
        (store/list-all-pages
         :asset
         store/list-fn
         {:query "*"}
         identity-map
         {}
         services))

       ))))
