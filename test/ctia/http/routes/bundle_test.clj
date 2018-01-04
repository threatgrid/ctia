(ns ctia.http.routes.bundle-test
  (:refer-clojure :exclude [get])
  (:require [ctia.http.routes.bundle :as sut]
            [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.test :as t
             :refer [deftest is join-fixtures testing use-fixtures]]
            [ctim.domain.id :as id]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [clojure.set :as set]))

(defn fixture-properties:small-max-bulk-size [test]
  ;; Note: These properties may be overwritten by ENV variables
  (helpers/with-properties ["ctia.http.bulk.max-size" 100]
    (test)))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    fixture-properties:small-max-bulk-size
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn mk-sighting
  [n]
  {:id (id/make-transient-id nil)
   :external_ids [(str "ctia-sighting-" n)]
   :description (str "description: sighting-" n)
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :observed_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :count 1
   :source "source"
   :sensor "endpoint.sensor"
   :confidence "High"})

(defn mk-indicator
  [n]
  {:id (id/make-transient-id nil)
   :external_ids [(str "ctia-indicator-" n)]
   :title (str "indicator-" n)
   :description (str "description: indicator-" n)
   :producer "producer"
   :indicator_type ["C2" "IP Watchlist"]
   :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

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
   :source_uri "http://example.com"
   :relationship_type relation-type
   :source_ref (:id source)
   :target_ref (:id target)})

(deftest valid-external-id-test
  (is (= "ctia-1"
         (sut/valid-external-id ["invalid-1" "invalid-2"  "ctia-1"]
                                ["ctia-" "cisco-"])))
  (is (nil? (sut/valid-external-id ["invalid-1" "invalid-2"  "ctia-1" "cisco-1"]
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
          (is (= (select-keys entity (keys original-entity))
                 (assoc original-entity :id id))))))))

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

(deftest-for-each-store bundle-import-test
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

        (is (every? #(= "create" %)
                    (map :action (:results bundle-result)))
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

        (is (every? #(= "update" %)
                    (map :action (:results bundle-result)))
            "All entities are updated")

        (doseq [entity (concat (:indicators bundle)
                               (:sightings bundle)
                               (map #(resolve-ids bundle-result %)
                                    (:relationships bundle)))]
          (validate-entity-record
           (find-result-by-original-id bundle-result (:id entity))
           entity))))
    (testing "Custom external prefix keys"
      (let [bundle {:type "bundle"
                    :source "source"
                    :indicators (hash-set
                                 (assoc (first indicators)
                                        :external_ids
                                        ["custom-2"]))}
            response-create (post "ctia/bundle/import?external-key-prefixes=custom-"
                                  :body bundle
                                  :headers {"Authorization" "45c1f5e3f05d0"})
            bundle-result-create (:parsed-body response-create)
            response-update (post "ctia/bundle/import?external-key-prefixes=custom-"
                                  :body bundle
                                  :headers {"Authorization" "45c1f5e3f05d0"})
            bundle-result-update (:parsed-body response-update)]
        (is (= 200 (:status response-create)))
        (is (= 200 (:status response-update)))
        (is (every? #(= "create" %)
                    (map :action (:results bundle-result-create)))
            "All entities are created")
        (is (every? #(= "update" %)
                    (map :action (:results bundle-result-update)))
            "All entities are updated")))))
