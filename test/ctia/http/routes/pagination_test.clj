(ns ctia.http.routes.pagination-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.url :as url]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [join-fixtures testing use-fixtures]]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [assert-post]]
             [pagination :refer [pagination-test
                                 pagination-test-no-sort]]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]
            [ctim.generators.schemas :as gs]
            [ring.util.codec :refer [url-encode]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-allow-all-auth]))

(deftest-for-each-store ^:slow test-pagination-lists
  "generate an observable and many records of all listable entities"
  (testing "with pagination test setup"
    (let [observable {:type "ip" :value "1.2.3.4"}
          indicators (->> (gs/sample-by-kw 5 :new-indicator)
                          (map #(assoc % :title "test")))
          created-indicators (map #(assert-post "ctia/indicator" %) indicators)
          indicator-rels (map (fn [{:keys [id]}] {:indicator_id id})
                              created-indicators)
          judgements (->> (gs/sample-by-kw 5 :new-judgement)
                          (map #(assoc %
                                       :observable observable
                                       :disposition 5
                                       :disposition_name "Unknown"
                                       :indicators indicator-rels)))
          sightings (->> (gs/sample-by-kw 5 :new-sighting)
                         (map #(-> (assoc % :observables [observable] :indicators indicator-rels)
                                   (dissoc % :relations))))
          route-pref (str "ctia/" (:type observable) "/" (:value observable))]

      (doseq [sighting sightings]
        (assert-post "ctia/sighting" sighting))
      (doseq [judgement judgements]
        (assert-post "ctia/judgement" judgement))

      (testing "test paginated lists responses"
        (pagination-test-no-sort (str route-pref "/indicators")
                                 {"api_key" "45c1f5e3f05d0"}
                                 [])
        (when (= "es" (get-in @ctia.properties/properties [:ctia :store :indicator]))
          (pagination-test (str "/ctia/indicator/search?query="
                                (-> indicators first :title))
                           {"api_key" "45c1f5e3f05d0"}
                           [:id :title]))
        (pagination-test (str "/ctia/indicator/"
                              (-> created-indicators
                                  first
                                  :id
                                  id/long-id->id
                                  :short-id
                                  url/encode)
                              "/sightings")
                         {"api_key" "45c1f5e3f05d0"}
                         [:id :timestamp :confidence])
        (pagination-test (str route-pref "/sightings")
                         {"api_key" "45c1f5e3f05d0"}
                         [:id :timestamp :confidence])
        (pagination-test (str route-pref "/judgements")
                         {"api_key" "45c1f5e3f05d0"}
                         [:id :disposition :priority :severity :confidence])))))
