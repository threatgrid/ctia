(ns ctia.http.routes.pagination-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [join-fixtures testing use-fixtures]]
            [ctia.schemas
             [indicator :refer [NewIndicator]]
             [judgement :refer [NewJudgement]]
             [sighting :refer [NewSighting]]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [api-key test-post]]
             [pagination :refer [pagination-test]]
             [store :refer [deftest-for-each-store]]]
            [ring.util.codec :refer [url-encode]]
            [schema-generators.generators :as g]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store ^:slow test-pagination-lists
  "generate an observable and many records of all listable entities"
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")

  (testing "with pagination test setup"
    (let [observable {:type "ip" :value "1.2.3.4"}
          indicators (->> (g/sample 5 NewIndicator)
                          (map #(assoc % :title "test")))
          created-indicators (map #(test-post "ctia/indicator" %) indicators)
          indicator-rels (map (fn [{:keys [id]}] {:indicator_id id}) created-indicators)
          judgements (->> (g/sample 5 NewJudgement)
                          (map #(assoc % :observable observable
                                       :disposition 5
                                       :disposition_name "Unknown"
                                       :indicators indicator-rels)))
          sightings (->> (g/sample 5 NewSighting)
                         (map #(-> (assoc % :observables [observable] :indicators indicator-rels)
                                   (dissoc % :relations))))

          route-pref (str "ctia/" (:type observable) "/" (:value observable))]

      (doall (map #(test-post "ctia/sighting" %) sightings))
      (doall (map #(test-post "ctia/judgement" %) judgements))

      (testing "test paginated lists responses"
        (pagination-test (str route-pref "/indicators")
                         {"api_key" "45c1f5e3f05d0"} [:id :title])
        (pagination-test (str "/ctia/indicator/title/" (-> indicators first :title))
                         {"api_key" "45c1f5e3f05d0"} [:id :title])
        (pagination-test (str "/ctia/indicator/" (-> created-indicators first :id) "/sightings")
                         {"api_key" "45c1f5e3f05d0"} [:id :timestamp :confidence])
        (pagination-test (str route-pref "/sightings")
                         {"api_key" "45c1f5e3f05d0"} [:id :timestamp :confidence])
        (pagination-test (str route-pref "/judgements")
                         {"api_key" "45c1f5e3f05d0"} [:id :disposition :priority :severity :confidence])))))
