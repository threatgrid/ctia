(ns ctia.http.routes.pagination-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.spec.alpha :as cs]
            [clojure.spec.gen.alpha :as csg]
            [clojure.test :refer [deftest testing use-fixtures]]
            [ctia.properties :as p]
            [ctia.test-helpers
             [core :as helpers :refer [url-id]]
             [http :refer [app->HTTPShowServices assert-post]]
             [pagination :refer [pagination-test
                                 pagination-test-no-sort]]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :as id]))

(use-fixtures :once
  mth/fixture-schema-validation
  helpers/fixture-allow-all-auth)

(deftest ^:slow test-pagination-lists
  "generate an observable and many records of all listable entities"
  (test-for-each-store-with-app
   (fn [app]
     (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)

           http-show (p/get-http-show (app->HTTPShowServices app))
           observable {:type "ip"
                       :value "1.2.3.4"}
           title "test"
           new-indicators (->> (csg/sample (cs/gen :new-indicator/map 5))
                               (map #(assoc % :title title))
                               (map #(assoc % :id (url-id :indicator (app->HTTPShowServices app)))))
           created-indicators (map #(assert-post app "ctia/indicator" %)
                                   new-indicators)
           new-judgements (->> (csg/sample (cs/gen :new-judgement/map) 5)
                               (map #(assoc %
                                            :observable observable
                                            :disposition 5
                                            :disposition_name "Unknown"))
                               (map #(assoc % :id (url-id :judgement (app->HTTPShowServices app)))))
           new-sightings (->> (csg/sample (cs/gen :new-sighting/map) 5)
                              (map #(-> (assoc %
                                               :observables [observable])
                                        (dissoc % :relations :data)))
                              (map #(assoc % :id (url-id :sighting (app->HTTPShowServices app)))))
           route-pref (str "ctia/" (:type observable) "/" (:value observable))]

       (testing "setup: create sightings and their relationships with indicators"
         (doseq [new-sighting new-sightings
                 :let [{id :id} (assert-post app "ctia/sighting" new-sighting)
                       sighting-id (id/->id :sighting id http-show)]]
           (doseq [{id :id} created-indicators
                   :let [indicator-id (id/->id :indicator id http-show)]]
             (assert-post app
                          "ctia/relationship"
                          {:source_ref (id/long-id sighting-id)
                           :relationship_type "indicates"
                           :target_ref (id/long-id indicator-id)}))))

       (testing "setup: create judgements and their relationships with indicators"
         (doseq [new-judgement new-judgements
                 :let [{id :id} (assert-post app "ctia/judgement" new-judgement)
                       judgement-id (id/->id :judgement id http-show)]]
           (doseq [{id :id} created-indicators
                   :let [indicator-id (id/->id :indicator id http-show)]]
             (assert-post app
                          "ctia/relationship"
                          {:source_ref (id/long-id judgement-id)
                           :relationship_type "observable-of"
                           :target_ref (id/long-id indicator-id)}))))

       (testing "indicators with query (ES only)"
         (when (= "es" (get-in-config [:ctia :store :indicator]))
           (pagination-test app
                            (str "/ctia/indicator/search?query=" title)
                            {"Authorization" "45c1f5e3f05d0"}
                            [:id :title])))

       (testing "sightings by observable"
         (pagination-test app
                          (str route-pref "/sightings")
                          {"Authorization" "45c1f5e3f05d0"}
                          [:id
                           :timestamp
                           :confidence
                           :observed_time.start_time]))

       (testing "sightings/indicators by observable"
         (pagination-test-no-sort app
                                  (str route-pref "/sightings/indicators")
                                  {"Authorization" "45c1f5e3f05d0"}))

       (testing "judgements by observable"
         (pagination-test app
                          (str route-pref "/judgements")
                          {"Authorization" "45c1f5e3f05d0"}
                          [:id
                           :disposition
                           :priority
                           :severity
                           :confidence
                           :valid_time.start_time]))

       (testing "judgements/indicators by observable"
         (pagination-test-no-sort app
                                  (str route-pref "/judgements/indicators")
                                  {"Authorization" "45c1f5e3f05d0"}))))))
