(ns ctia.stores.atom.sighting-test
  (:require [clojure.test :refer [is deftest testing] :as t]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as tcg]
            [clojure.test.check.properties :refer [for-all]]
            [ctia.stores.atom.sighting :as sut]
            [ctia.test-helpers.generators.schemas :as gen]
            [ctia.test-helpers.generators.schemas.sighting-generators :as sg]))

(defspec spec-handle-list-sightings-by-indicators
  (for-all [[indicator sightings] gen/gen-indicator-with-sightings]
           (let [store (->> sightings
                            (map (fn [x] [(:id x) x]))
                            (into {})
                            atom)]
             (and
              ;; Empty search
              (empty? (:data (sut/handle-list-sightings-by-indicators store [] {})))
              ;; Basic search
              (= (set (vals @store))
                 (-> (sut/handle-list-sightings-by-indicators store [indicator] {})
                     :data
                     set))))))

(def gen-observable-and-sightings
  (tcg/let [observable (gen/gen-entity :observable)
            different-observables (tcg/vector
                                   (tcg/such-that
                                    (partial not= observable)
                                    (gen/gen-entity :observable))
                                   1 20)
            sightings (tcg/vector
                       (sg/gen-sighting-with-observables
                        (cons observable different-observables))
                       1 20)]
    [observable
     sightings]))

(defspec spec-handle-list-sightings-by-observables-atom
  (for-all [[observable sightings] gen-observable-and-sightings]
           (let [store (->> sightings
                            (map (fn [x] [(:id x) x]))
                            (into {})
                            atom)]
             (and
              ;; Empty search
              (empty? (:data (sut/handle-list-sightings-by-observables store [] {})))
              ;; Basic search
              (= (set (vals @store))
                 (-> (sut/handle-list-sightings-by-observables store [observable] {})
                     :data
                     set))))))
