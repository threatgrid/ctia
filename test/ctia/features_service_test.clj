(ns ctia.features-service-test
  (:require  [clojure.test :refer [deftest are is join-fixtures testing use-fixtures]]
             [ctia.test-helpers.core :as th]))

(deftest features-service-test
  (testing "FeaturesService methods for entity marked as disabled in config"
    (th/with-properties ["ctia.features.disable" "asset,actor,sighting"]
      (th/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [disabled? enabled?]} (th/get-service-map app :FeaturesService)]
           (testing "Asset, Actor and Sighting are disabled"
             (is (not-any? enabled? [:asset :actor :sighting])))
           (testing "Incident, Indicator entities are enabled"
             (is (every? enabled? [:incident :indicator])))))))))

(deftest routes-for-disabled-entities-test
  (testing "No http routes should exist for disabled entities"
    (th/with-properties ["ctia.features.disable" "asset,actor,sighting"]
      (th/fixture-ctia-with-app
       (fn [app]
         (are [entity status-code] (->> entity
                                        name
                                        (format "ctia/%s/search")
                                        (th/GET app)
                                        :status
                                        (= status-code))

           :asset            404
           :actor            404
           :sighting         404
           :asset-mapping    200
           :indicator        200
           :asset-properties 200))))))
