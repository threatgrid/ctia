(ns ctia.features-service-test
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest are is testing use-fixtures]]
   [ctia.entity.entities :as entities]
   [ctia.test-helpers.core :as th]
   [ctia.test-helpers.es :as es-helpers]))

(use-fixtures :each es-helpers/fixture-properties:es-store)

(deftest features-service-test
  (testing "FeaturesService methods for entity marked as disabled in config"
    (th/with-properties ["ctia.features.disable" "asset,actor,sighting"]
      (th/fixture-ctia-with-app
       (fn [app]
         (let [{:keys [enabled?]} (th/get-service-map app :FeaturesService)]
           (testing "Asset, Actor and Sighting are disabled"
             (is (not-any? enabled? [:asset :actor :sighting])))
           (testing "Incident, Indicator entities are enabled"
             (is (every? enabled? [:incident :indicator])))
           (testing "It should not return `true` for non-existing entity keys"
             (is (not (enabled? :lorem-ipsum))))))))))

(deftest routes-for-disabled-entities-test
  (let [try-route (fn [app entity]
                    (->> entity
                         name
                         (format "ctia/%s/search")
                         (th/GET app)
                         :status))]
    (testing "http routes should exist for entities that aren't explicitly disabled in the config"
      (th/with-properties []
        (th/fixture-ctia-with-app
         (fn [app]
           (are [entity status-code] (is (= status-code (try-route app entity)))
             :asset            200
             :actor            200
             :sighting         200)))))
    (testing "No http routes should exist for disabled entities"
      (th/with-properties ["ctia.features.disable" "asset,actor,sighting"]
        (th/fixture-ctia-with-app
         (fn [app]
           (are [entity status-code] (is (= status-code (try-route app entity)))
             :asset            404
             :actor            404
             :sighting         404)))))))
