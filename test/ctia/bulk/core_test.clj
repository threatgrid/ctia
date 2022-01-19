(ns ctia.bulk.core-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctim.domain.id :as id]
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
            [ctim.examples.sightings :refer [sighting-minimal]]
            [ctim.examples.indicators :refer [indicator-minimal]]
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

(deftest to-long-id-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)]
       (is (id/long-id?
            (sut/to-long-id
             "sighting-2ea50927-d4e7-449a-bb86-b3be4737437a"
             services))
           "return a long id for valid short ids")
       (is (= "http://localhost:3000/ctia/sighting/sighting-2ea50927-d4e7-449a-bb86-b3be4737437a"
              (sut/to-long-id
               "http://localhost:3000/ctia/sighting/sighting-2ea50927-d4e7-449a-bb86-b3be4737437a"
               services))
           "preserve long ids")
       (is (= "anything else"
              (sut/to-long-id
               "anything else"
               services))
           "preservce invalid ids")))))

(deftest make-bulk-result-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)
           updated-indicators (repeatedly 3 #(make-id "indicator"))
           not-found-indicators (repeatedly 2 #(make-id "indicator"))
           forbidden-indicators (repeatedly 1 #(make-id "indicator"))
           results {:updated updated-indicators
                    :errors {:not-found not-found-indicators
                             :forbidden forbidden-indicators}}
           short-ids->long-ids (fn [ids]
                                 (set
                                  (map #(short-id->long-id % services)
                                       ids)))
           updated-long-ids (short-ids->long-ids updated-indicators)
           not-found-long-ids (short-ids->long-ids not-found-indicators)
           forbidden-long-ids (short-ids->long-ids forbidden-indicators)
           base-expected {:updated updated-long-ids
                          :errors {:not-found not-found-long-ids
                                   :forbidden forbidden-long-ids}}
           with-sets (fn [{:keys [errors] :as res}]
                       (cond-> (update res :updated set)
                         (:not-found errors) (update-in [:errors :not-found] set)
                         (:forbidden errors) (update-in [:errors :forbidden] set)))]
       (is (= base-expected
              (with-sets (sut/format-bulk-flow-res results services)))
           "format-bulk-flow-res shall return results with long ids")
       (let [base-fake-flowmap {:create-event-fn identity
                                :entities []
                                :entity-type :indicator
                                :flow-type :update
                                :services services
                                :identity (map->Identity {:login "user1" :groups ["g1"]})
                                :store-fn identity}]
         (is (= base-expected
                (-> (assoc base-fake-flowmap :results results)
                    sut/make-bulk-result
                    with-sets)))
         (is (= (update-in base-expected [:errors :not-found] conj "not-found-id")
                (-> (assoc base-fake-flowmap :results results :not-found ["not-found-id"])
                    sut/make-bulk-result
                    with-sets))
             "in case of race condition (an entity deleted during flow) not-found ids are concatenated")
         (is (= (assoc base-expected :errors {:not-found not-found-long-ids})
                (-> (assoc base-fake-flowmap
                           :results (dissoc results :errors)
                           :not-found not-found-indicators)
                    sut/make-bulk-result
                    with-sets))
             "not found ids are properly added to empty errors"))))))

(deftest bulk-crud-test
  (helpers/with-properties
    ;; set max record visibility to everyone to test public CTIA case where
    ;; some entities are visibile to some groups but not editable by their users
    ["ctia.access-control.max-record-visibility" "everyone"]
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             sighting-store (helpers/get-store app :sighting)
             indicator-store (helpers/get-store app :indicator)
             ident-map {:login "guigui"
                        :groups ["ireaux"]}
             ident (map->Identity ident-map)
             other-group-ident-map {:login "john doe"
                                    :groups ["another group"]}
             other-group-ident (map->Identity other-group-ident-map)
             fixtures-green (with-tlp (select-keys (fixt/bundle 3 false) [:sightings :indicators])
                              "green")
             fixtures-amber (with-tlp (select-keys (fixt/bundle 2 false) [:sightings :indicators])
                              "amber")
             ;; update / delete helpers
             missing-id-1 (short-id->long-id (make-id "indicator") services)
             missing-id-2 (short-id->long-id (make-id "indicator") services)
             missing-indicator-ids [missing-id-1 missing-id-2]
             nb-not-found (fn [res ent] (-> res ent :errors :not-found count))
             nb-forbidden (fn [res ent] (-> res ent :errors :forbidden count))
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

         (let [sighting-patches (map #(assoc {:source "patched sighting"}
                                             :id %)
                                     sighting-ids)
               indicator-patches (map #(assoc {:source "patched indicator"}
                                              :id %)
                                      indicator-ids)
               missing-indicator-patches (map #(assoc {:source "patched indicator"}
                                                      :id %)
                                              missing-indicator-ids)
               sighting-updates (map #(assoc sighting-minimal
                                             :source "updated sighting"
                                             :id %)
                                     sighting-ids)
               indicator-updates (map #(assoc indicator-minimal
                                              :source "updated indicator"
                                              :id %)
                                      indicator-ids)
               missing-indicator-updates (map #(assoc indicator-minimal
                                                      :source "updated indicator"
                                                      :id %)
                                              missing-indicator-ids)
               bulk-patch {:sightings sighting-patches
                           :indicators (concat indicator-patches
                                               missing-indicator-patches)}
               bulk-update {:sightings sighting-updates
                            :indicators (concat indicator-updates
                                                missing-indicator-updates)}
               bulk-delete {:sightings sighting-ids
                            :indicators (concat indicator-ids missing-indicator-ids)}
               check-tlp (fn [other-group-res]
                           (testing "non visible entities are returned as not-found errors"
                             (is (= 2 (nb-not-found other-group-res :sightings)))
                             (is (= 4 (nb-not-found other-group-res :indicators))))
                           (is (= 3
                                  (nb-forbidden other-group-res :sightings)
                                  (nb-forbidden other-group-res :indicators))
                               "visible entities that the user is not allowed to write on are returned as forbidden errors"))]
           (testing "bulk-patch shall properly patch submitties entitites"
             (let [other-group-res (sut/patch-bulk bulk-patch
                                                   other-group-ident
                                                   {:refresh "true"}
                                                   services)
                   {:keys [sightings indicators]}
                   (sut/patch-bulk bulk-patch
                                   ident
                                   {:refresh "true"}
                                   services)]
               (check-tlp other-group-res)
               (is (= #{missing-id-1 missing-id-2} (set (get-in indicators [:errors :not-found]))))
               (is (nil? (:not-found sightings)))
               (is (= (set sighting-ids) (set (:updated sightings))))
               (is (= (set indicator-ids) (set (:updated indicators))))
               (doseq [sighting-id (:updated sightings)]
                 (is (= "patched sighting"
                        (:source (read-record sighting-store sighting-id ident-map {})))))
               (doseq [indicator-id (:updated indicators)]
                 (is (= "patched indicator"
                        (:source (read-record indicator-store indicator-id ident-map {})))))))

           (testing "bulk-update shall properly update submitties entitites"
             (let [other-group-res (sut/update-bulk bulk-update
                                                   other-group-ident
                                                   {:refresh "true"}
                                                   services)
                   {:keys [sightings indicators]}
                   (sut/update-bulk bulk-update
                                   ident
                                   {:refresh "true"}
                                   services)]
               (check-tlp other-group-res)
               (is (= #{missing-id-1 missing-id-2} (set (get-in indicators [:errors :not-found]))))
               (is (nil? (:not-found sightings)))
               (is (= (set sighting-ids) (set (:updated sightings))))
               (is (= (set indicator-ids) (set (:updated indicators))))
               (doseq [sighting-id (:updated sightings)]
                 (is (= "updateed sighting"
                        (:source (read-record sighting-store sighting-id ident-map {})))))
               (doseq [indicator-id (:updated indicators)]
                 (is (= "updateed indicator"
                        (:source (read-record indicator-store indicator-id ident-map {})))))))

           (testing "bulk-delete shall properly delete entities with allowed entities and manage visibilities"
             (let [other-group-res (sut/delete-bulk bulk-delete
                                                    other-group-ident
                                                    {:refresh "true"}
                                                    services)
                   {:keys [sightings indicators]}
                   (sut/delete-bulk {:sightings sighting-ids
                                     :indicators (concat indicator-ids
                                                         [missing-id-1
                                                          missing-id-2])}
                                    ident
                                    {:refresh "true"}
                                    services)]
               (check-tlp other-group-res)
               (is (= #{missing-id-1 missing-id-2} (set (get-in indicators [:errors :not-found]))))
               (is (nil? (:not-found sightings)))
               (is (= (set sighting-ids) (set (:deleted sightings))))
               (is (= (set indicator-ids) (set (:deleted indicators))))
               (doseq [sighting-id (:deleted sightings)]
                 (is (nil? (read-record sighting-store sighting-id ident-map {}))))
               (doseq [indicator-id (:deleted indicators)]
                 (is (nil? (read-record indicator-store indicator-id ident-map {}))))))))))))
