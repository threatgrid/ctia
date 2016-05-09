(ns ctia.stores.atom.sighting-test
  (:require [ctia.stores.atom.sighting :as sut]
            [clojure.test :refer [is deftest testing] :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [schema-generators.generators :as g]
            [ctia.schemas.common :refer [Observable]]
            [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.sighting :refer [StoredSighting]]))

(deftest handle-list-sightings-by-indicators-test
  (doseq [indicator (->> (g/sample 20 StoredIndicator)
                         (map #(update-in % [:id] str "ind-")))]
    (let [sightings (->> (g/sample 20 StoredSighting)
                         (map #(update-in % [:id] str "sig-"))
                         (map (fn [x]
                                (into x
                                      {:indicators
                                       [{:indicator_id (:id indicator)}]}))))
          store (->> sightings
                     (map (fn [x] {(:id x) x}))
                     (reduce into {})
                     atom)]
      (testing "Empty search"
        (is (empty? (sut/handle-list-sightings-by-indicators store []))))
      (testing "basic search"
        (is (= (set (vals @store))
               (set (sut/handle-list-sightings-by-indicators store [indicator]))))))))

(deftest handle-list-sightings-by-observables-atom-test
  (doseq [observable (g/sample 20 Observable)]
    (let [random-observables (remove #(= observable %) (g/sample 20 Observable))
          sightings (->> (g/sample 20 StoredSighting)
                         (map #(update-in % [:id] str "sig-"))
                         (map (fn [x] (into x {:observables (cons observable
                                                                  random-observables)}))))
          store (->> sightings
                     (map (fn [x] {(:id x) x}))
                     (reduce into {})
                     atom)]
      (testing "Empty search"
        (is (empty? (sut/handle-list-sightings-by-observables store []))))
      (testing "Basic search"
        (is (= (set (vals @store))
               (set (sut/handle-list-sightings-by-observables store [observable]))))))))
