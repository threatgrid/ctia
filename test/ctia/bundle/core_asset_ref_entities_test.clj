(ns ctia.bundle.core-asset-ref-entities-test
  "Additonal tests for entities with AssetRef field.

  For Assets, (unlike other entities) it was decided not to use Relationship
  objects to define association with other entities. Specifically, AssetMap and
  AssetProperties are linked to Assets via their :asset-ref fields.

  These tests are to ensure that such a relationship is observed when these
  types of objects when they created via Bundle Import"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.walk :as walk]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.bulk.core :as bulk]
   [ctia.bundle.core :as bundle]
   [ctia.store :as store]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctia.test-helpers.auth :as auth]
   [ctia.test-helpers.core :as th]
   [ctim.examples.bundles :refer [bundle-maximal]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.auth.capabilities :refer [all-capabilities]]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.test :refer [validate-schemas]]))

(use-fixtures :once
              validate-schemas
              whoami-helpers/fixture-server)

(def ^:private login
  (map->Identity {:login  "foouser"
                  :groups ["foogroup"]
                  :capabilities (all-capabilities)}))

(defn- set-transient-asset-refs [x]
  (walk/prewalk
   (fn [m]
     (cond
       (not (map? m)) m

       (contains? m :asset_ref)
       (assoc m :asset_ref "transient:asset-1")

       (-> m :type (= "asset"))
       (assoc m :id "transient:asset-1")

       :else m)) x))

(def bundle-ents
  "Sample Bundle Map for testing."
  (-> bundle-maximal
      (select-keys
       [:assets :asset_mappings :asset_properties ])
      th/deep-dissoc-entity-ids
      ;; in order to test association of Asset to AssetMappings/AssetProperties
      set-transient-asset-refs))

(deftest asset-refs-test
  (th/fixture-ctia-with-app
   (fn [app]
     (th/set-capabilities! app "foouser" ["foogroup"] "user" auth/all-capabilities)
     (testing "every AssetMapping and AssetProperties have proper
                 non-transient :asset-ref"
       (let [services          (app/service-graph app)
             {:keys [results]} (bundle/import-bundle
                                bundle-ents
                                nil         ;; external-key-prefixes
                                login
                                services)
             get-records       (fn [store]
                                 (let [{{:keys [get-store]} :StoreService} services]
                                   (-> (get-store store)
                                       (store/list-records
                                        {:query "*"}
                                        {:login  "foouser"
                                         :groups ["foogroup"]} {})
                                       :data)))
             asset-refs        (->> results
                                  (filter #(-> % :result (= "created")))
                                  (filter #(-> % :type (= :asset)))
                                  (map :id)
                                  set)]
         (assert (seq asset-refs))
         (doseq [store [:asset-mapping :asset-properties]
                 :let [refs (->> store get-records (map :asset_ref))]]
           (assert (seq refs))
           (is (every? (partial contains? asset-refs) refs))))))))

(deftest validate-asset-refs-test
  (testing "Bundle with asset_refs that have no corresponding Asset"
    (let [;; assign non-transient ID to the asset in the Bundle,
          ;; leaving :asset_ref's pointing to a transient ID that would never resolve
          bundle (walk/prewalk
                  #(if (and (map? %)
                            (-> % :type (= "asset")))
                     (assoc % :id "http://ex.tld/ctia/asset/asset-61884b14-e273-4930-a5ff-dcce69207724")
                     %)
                  bundle-ents)]
      (test-for-each-store-with-app
       (fn [app]
         (th/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
         (whoami-helpers/set-whoami-response app
                                             "45c1f5e3f05d0"
                                             "foouser"
                                             "foogroup"
                                             "user")
         (let [create-response (th/POST app
                                        "ctia/bundle/import"
                                        :body bundle
                                        :headers {"Authorization" "45c1f5e3f05d0"})
               res (:parsed-body create-response)]
           (when (is (= 200 (:status create-response)))
             (is (= [{:result "error", :type :asset
                      :external_ids ["http://ex.tld/ctia/asset/asset-61884b14-e273-4930-a5ff-dcce69207724"]}
                     {:result "error", :type :asset-mapping
                      :external_ids ["http://ex.tld/ctia/asset-mapping/asset-mapping-636ef2cc-1cb0-47ee-afd4-ecc1fe4be451"]}
                     {:result "error", :type :asset-properties
                      :external_ids ["http://ex.tld/ctia/asset-properties/asset-properties-97c3dbb5-6deb-4eed-b6d7-b77fa632cc7b"]}]
                    (map #(dissoc % :error) (sort-by :type (:results res))))
                 (pr-str res))))))))
  (testing "Bundle with asset_refs that aren't transient"
    (let [;; :asset_ref's that are non-transient should still be allowed
          bundle (walk/prewalk
                  #(if (and (map? %)
                            (contains? % :asset_ref))
                     (assoc % :asset_ref "http://ex.tld/ctia/asset/asset-61884b14-e273-4930-a5ff-dcce69207724")
                     %)
                  bundle-ents)]
      (test-for-each-store-with-app
       (fn [app]
         (th/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
         (whoami-helpers/set-whoami-response app
                                             "45c1f5e3f05d0"
                                             "foouser"
                                             "foogroup"
                                             "user")
         (let [create-response (th/POST app
                                        "ctia/bundle/import"
                                        :body bundle
                                        :headers {"Authorization" "45c1f5e3f05d0"})
               {:keys [results]} (:parsed-body create-response)
               num-created (->> results
                                (map :result)
                                (keep #{"created"})
                                count)]
           (when (is (= 200 (:status create-response)))
             (is (= (count bundle-ents)
                    num-created)))))))))
