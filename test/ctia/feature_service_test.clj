(ns ctia.feature-service-test
  (:require  [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
             [ctia.test-helpers.core :as th]))

(defn- properties-fixture [t]
  (th/with-properties
    ["ctia.features.disable" "asset,actor,sighting"]
    (t)))

(use-fixtures :each (join-fixtures [properties-fixture th/fixture-ctia-fast]))

(deftest features-service-test
  (let [app (th/get-current-app)
        {:keys [disabled? enabled?]} (th/get-service-map app :FeaturesService)]
    (testing "Asset, Actor and Sighting are disabled"
      (is (every? disabled? [:asset :actor :sighting]))
      (is (every? (comp not enabled?) [:asset :actor :sighting])))
    (testing "Incident, Indicator entities are enabled"
      (is (every? (comp not disabled?) [:incident :indicator]))
      (is (every? enabled? [:incident :indicator])))))
