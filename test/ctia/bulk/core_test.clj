(ns ctia.bulk.core-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.auth.allow-all :refer [identity-singleton]]
            [ctia.store :refer [read-record]]
            [ctia.bulk.core :as sut]
            [ctia.bulk.schemas :refer [NewBulk]]
            [ctia.features-service :as features-svc]
            [ctia.flows.crud :refer [make-id]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [ctia.domain.entities :refer [short-id->long-id]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap
             :refer
             [with-app-with-config]]
            [schema-tools.core :as st]))

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

(deftest test-format-bulk-flow-res
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)
           bulk-flow-res
           {:deleted
            '("tool-2ea50927-d4e7-449a-bb86-b3be4737437a"
              "tool-4487e07d-a442-432b-9e19-2b934e5dff42"
              "tool-e270b53e-f6bf-47c4-81d9-52611c75d9d3")
            :errors {:not-found '("tool-71240bee-3915-44dd-af7d-d4b650d71723")
                     :forbidden '("tool-82240bee-3915-44dd-af7d-d4b650d71724")}}
           port (helpers/get-http-port app)
           to-long-ids #(format "http://localhost:%d/ctia/tool/%s" port %)
           expected
           {:deleted
            (map to-long-ids
                 '("tool-2ea50927-d4e7-449a-bb86-b3be4737437a"
                   "tool-4487e07d-a442-432b-9e19-2b934e5dff42"
                   "tool-e270b53e-f6bf-47c4-81d9-52611c75d9d3"))
            :errors {:not-found [(to-long-ids "tool-71240bee-3915-44dd-af7d-d4b650d71723")]
                     :forbidden [(to-long-ids "tool-82240bee-3915-44dd-af7d-d4b650d71724")]}}]
       (is (= expected (sut/format-bulk-flow-res bulk-flow-res services)))))))


(defn with-tlp
  [fixtures tlp]
  (into {}
        (map (fn [[k v]]
               {k (map #(assoc % :tlp tlp) v)}))
        fixtures))

(deftest bulk-create-delete
  (helpers/with-properties
    ["ctia.access-control.max-record-visibility" "everyone"]
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             sighting-store (helpers/get-store app :sighting)
             indicator-store (helpers/get-store app :indicator)
             ident-map {:login "guigui"
                        :groups ["ireaux"]}
             ident (map->Identity ident-map)

             fixtures-green (with-tlp (select-keys (fixt/bundle 3 false) [:sightings :indicators])
                              "green")
             fixtures-amber (with-tlp (select-keys (fixt/bundle 2 false) [:sightings :indicators])
                              "amber")
             fixtures (merge-with concat fixtures-green fixtures-amber)
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

         (testing "bulk-delete shall properly delete entities with allowed entities and manage visibilities"
           (let [other-group-ident {:login "john doe"
                                    :groups ["another group"]}
                 other-group-res (sut/delete-bulk {:sightings sighting-ids
                                                   :indicators indicator-ids}
                                                  (map->Identity other-group-ident)
                                                  {:refresh "true"}
                                                  services)
                 nb-not-found (fn [res ent] (-> res ent :errors :not-found count))
                 nb-forbidden (fn [res ent] (-> res ent :errors :forbidden count))
                 _ (is (= 2
                          (nb-not-found other-group-res :sightings)
                          (nb-not-found other-group-res :indicators))
                       "non visible entities are returned as not-found errors")
                 _ (is (= 3
                          (nb-forbidden other-group-res :sightings)
                          (nb-forbidden other-group-res :indicators))
                       "visible entities the user is not allowed to delete are returned as forbidden errors")
                 missing-id-1 (short-id->long-id (make-id "indicator") services)
                 missing-id-2 (short-id->long-id (make-id "indicator") services)
                 {:keys [sightings indicators] :as res}
                 (sut/delete-bulk {:sightings sighting-ids
                                   :indicators (concat indicator-ids
                                                       [missing-id-1
                                                        missing-id-2])}
                                  ident
                                  {:refresh "true"}
                                  services)]
             (is (= #{missing-id-1 missing-id-2} (set (get-in indicators [:errors :not-found]))))
             (is (nil? (:not-found sightings)))
             (is (= (set sighting-ids) (set (:deleted sightings))))
             (is (= (set indicator-ids) (set (:deleted indicators))))
             (doseq [sighting-id (:deleted sightings)]
               (is (nil? (read-record sighting-store sighting-id ident-map {}))))
             (doseq [indicator-id (:deleted indicators)]
               (is (nil? (read-record indicator-store indicator-id ident-map {})))))))))))
