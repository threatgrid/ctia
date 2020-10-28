(ns ctia.features-service-test
  (:require  [clojure.test :refer [deftest are is join-fixtures testing use-fixtures]]
             [ctia.test-helpers.core :as th]))

(defn- properties-fixture [t]
  (th/with-properties
    ["ctia.features.disable" "asset,actor,sighting"]
    (t)))

(use-fixtures :each (join-fixtures [properties-fixture]))

(deftest features-service-test
  (testing "FeaturesService methods for entity marked as disabled in config"
   (th/fixture-ctia-with-app
    (fn [app]
      (let [{:keys [disabled? enabled?]} (th/get-service-map app :FeaturesService)]
        (testing "Asset, Actor and Sighting are disabled"
          (is (every? disabled? [:asset :actor :sighting]))
          (is (every? (comp not enabled?) [:asset :actor :sighting])))
        (testing "Incident, Indicator entities are enabled"
          (is (every? (comp not disabled?) [:incident :indicator]))
          (is (every? enabled? [:incident :indicator]))))))))

(deftest routes-for-disabled-entities-test
  (testing "No http routes should exist for disabled entities"
   (th/fixture-ctia-with-app
    (fn [app]
      (are [entity status-code] (->> entity
                                     name
                                     (format "ctia/%s/search")
                                     (th/GET app)
                                     :status
                                     (= status-code))

        :asset            404
        :asset-mapping    200
        :actor            404
        :sighting         404
        :indicator        200
        :asset-properties 200)))))
