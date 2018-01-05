(ns ctia.http.routes.pagination-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [testing use-fixtures]]
            [clojure.spec.alpha :as cs]
            [clojure.spec.gen.alpha :as csg]
            [ctia.properties :refer [properties]]
            [ctia.schemas.core] ;; for spec side-effects
            [ctia.test-helpers
             [core :as helpers :refer [url-id]]
             [http :refer [assert-post]]
             [pagination :refer [pagination-test
                                 pagination-test-no-sort]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once
  mth/fixture-schema-validation
  helpers/fixture-properties:clean
  helpers/fixture-allow-all-auth)

(deftest-for-each-store ^:slow test-pagination-lists
  "generate an observable and many records of all listable entities"
  (let [http-show (get-in @properties [:ctia :http :show])
        observable {:type "ip"
                    :value "1.2.3.4"}
        title "test"
        new-indicators (->> (csg/sample (cs/gen :new-indicator/map 5))
                            (map #(assoc % :title title))
                            (map #(assoc % :id (url-id :indicator))))
        created-indicators (map #(assert-post "ctia/indicator" %)
                                new-indicators)
        new-judgements (->> (csg/sample (cs/gen :new-judgement/map) 5)
                            (map #(assoc %
                                         :observable observable
                                         :disposition 5
                                         :disposition_name "Unknown"))
                            (map #(assoc % :id (url-id :judgement))))
        new-sightings (->> (csg/sample (cs/gen :new-sighting/map) 5)
                           (map #(-> (assoc %
                                            :observables [observable])
                                     (dissoc % :relations)))
                           (map #(assoc % :id (url-id :sighting))))
        route-pref (str "ctia/" (:type observable) "/" (:value observable))]

    (testing "setup: create sightings and their relationships with indicators"
      (doseq [new-sighting new-sightings
              :let [{id :id} (assert-post "ctia/sighting" new-sighting)
                    sighting-id (id/->id :sighting id http-show)]]
        (doseq [{id :id} created-indicators
                :let [indicator-id (id/->id :indicator id http-show)]]
          (assert-post "ctia/relationship"
                       {:source_ref (id/long-id sighting-id)
                        :relationship_type "indicates"
                        :target_ref (id/long-id indicator-id)}))))

    (testing "setup: create judgements and their relationships with indicators"
      (doseq [new-judgement new-judgements
              :let [{id :id} (assert-post "ctia/judgement" new-judgement)
                    judgement-id (id/->id :judgement id http-show)]]
        (doseq [{id :id} created-indicators
                :let [indicator-id (id/->id :indicator id http-show)]]
          (assert-post "ctia/relationship"
                       {:source_ref (id/long-id judgement-id)
                        :relationship_type "observable-of"
                        :target_ref (id/long-id indicator-id)}))))

    (testing "indicators with query (ES only)"
      (when (= "es" (get-in @properties [:ctia :store :indicator]))
        (pagination-test (str "/ctia/indicator/search?query=" title)
                         {"Authorization" "45c1f5e3f05d0"}
                         [:id :title])))

    (testing "sightings by observable"
      (pagination-test (str route-pref "/sightings")
                       {"Authorization" "45c1f5e3f05d0"}
                       [:id
                        :timestamp
                        :confidence
                        :observed_time.start_time]))

    (testing "sightings/indicators by observable"
      (pagination-test-no-sort (str route-pref "/sightings/indicators")
                               {"Authorization" "45c1f5e3f05d0"}
                               []))

    (testing "judgements by observable"
      (pagination-test (str route-pref "/judgements")
                       {"Authorization" "45c1f5e3f05d0"}
                       [:id
                        :disposition
                        :priority
                        :severity
                        :confidence
                        :valid_time.start_time]))

    (testing "judgements/indicators by observable"
      (pagination-test-no-sort (str route-pref "/judgements/indicators")
                               {"Authorization" "45c1f5e3f05d0"}
                               []))))
