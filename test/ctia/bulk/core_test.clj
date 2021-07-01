(ns ctia.bulk.core-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.auth.allow-all :refer [identity-singleton]]
            [ctia.store :refer [read-record query-string-search]]
            [ctia.bulk.core :as sut]
            [ctia.bulk.schemas :refer [NewBulk]]
            [ctia.features-service :as features-svc]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap
             :refer
             [with-app-with-config]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [get-in-config (helpers/build-get-in-config-fn)
          res (sut/read-entities ["judgement-123"]
                                 :judgement
                                 identity-singleton
                                 {:ConfigService {:get-in-config get-in-config}
                                  :StoreService {:get-store (constantly nil)}})]
      (is (= [nil] res)))))

(defn schema->keys
  "Extracts both: required and optional keys of schema as set of keywords"
  [schema]
  (let [reqs (->> schema st/required-keys keys)
        opts (->> schema st/optional-keys keys (map :k))]
    (set (concat reqs opts))))

(deftest bulk-schema-excludes-disabled-test
  (testing "ensure NewBulk schema includes only enabled entities"
    (with-app-with-config app
      [features-svc/features-service] {:ctia {:features {}}}
      (let [bulk-schema (NewBulk (helpers/app->GetEntitiesServices app))]
        (is (set/subset? #{:assets :actors} (schema->keys bulk-schema)))))
    (with-app-with-config app
      [features-svc/features-service]
      {:ctia {:features {:disable "asset,actor"}}}
      (let [bulk-schema (NewBulk (helpers/app->GetEntitiesServices app))]
        (is (false? (set/subset? #{:assets :actors} (schema->keys bulk-schema))))))))

(deftest bulk-create-delete
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)

           sighting-store (helpers/get-store app :sighting)
           indicator-store (helpers/get-store app :indicator)
           ident-map {:login "guigui"
                      :groups ["ireaux"]}
           ident (map->Identity ident-map)
           ;;forein-ident ()
           fixtures (select-keys (fixt/bundle 5 false) [:sightings :indicators])
           with-errors (assoc fixtures
                              :actors
                              [{:a 1}])
           {sighting-ids :sightings indicator-ids :indicators :keys [tempids]}
           (sut/create-bulk with-errors
                            {}
                            ident
                            {:refresh "true"} services)]
       (testing "bulk-create shall properly create submitties entitites"
         (is (= 5
                (count sighting-ids)
                (count indicator-ids)))
         (is (= (set (vals tempids))
                (into (set sighting-ids) (set indicator-ids))))
         (doseq [sighting-id sighting-ids]
           (is (some? (read-record sighting-store sighting-id ident-map {}))))
         (doseq [indicator-id indicator-ids]
           (is (some? (read-record indicator-store indicator-id ident-map {})))))

       (testing "bulk-delete shall properly delete entities"
         (let [{:keys [sightings indicators]}
               (sut/delete-bulk {:sightings sighting-ids
                                 :indicators (concat indicator-ids
                                                     ["missing-1"
                                                      "missing-2"])}
                                ident
                                {:refresh "true"}
                                services)]
           (is (= ["missing-1" "missing-2"] (get-in indicators [:errors :not-found])))
           (is (nil? (:not-found sightings)))
           (is (:deleted sightings))
           (is (:deleted indicators))
           (doseq [sighting-id (:deleted sightings)]
             (is (nil? (read-record sighting-store sighting-id ident-map {}))))
           (doseq [indicator-id (:deleted indicators)]
             (is (nil? (read-record indicator-store indicator-id ident-map {}))))))))))
