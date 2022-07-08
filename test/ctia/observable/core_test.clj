(ns ctia.observable.core-test
  (:require [ctia.observable.core :as sut]
            [clojure.test :refer [deftest testing is join-fixtures use-fixtures]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.test :refer [validate-schemas]]
            [ctia.test-helpers.fixtures :as fixt]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(deftest test-observable->threat-ctx
  (helpers/fixture-ctia-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [services (app/service-graph app)
           bundle (fixt/incident-threat-ctx-bundle 2 true)
           sighting-observable (-> bundle :sightings first :observables first)
           judgement-observable (-> bundle :judgements first :observable)
           bundle-res (helpers/POST-bulk app bundle)
           identity-map {:login "foouser" :groups ["foogroup"]}
           incidents (:incidents bundle-res)
           indicators (:indicators bundle-res)]
       (assert (seq incidents))
       (assert (seq indicators))
       (is (= (set incidents)
              (set (sut/sighting-observable->incident-ids sighting-observable
                                                          identity-map
                                                          services))))
       (is (= (set indicators)
              (set (sut/sighting-observable->indicator-ids sighting-observable
                                                           identity-map
                                                           services))))
       (is (= (set indicators)
              (set (sut/judgement-observable->indicator-ids judgement-observable
                                                            identity-map
                                                            services))))))))

(deftest observables-within-date-range
  (helpers/fixture-ctia-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [services (app/service-graph app)
           bundle (fixt/sightings-threat-ctx-bundle 2 true)
           _bundle-res (helpers/POST-bulk app bundle)
           sighting-observable (-> bundle :sightings first :observables first)
           judgement-observable (-> bundle :judgements first :observable)
           identity-map {:login "foouser" :groups ["foogroup"]}
           observable->sightings #(:data (sut/observable->sightings
                                          sighting-observable
                                          identity-map
                                          %
                                          services))
           observable->judgements #(:data (sut/observable->judgements
                                          judgement-observable
                                          identity-map
                                          %
                                          services))
           now (t/now) ;; approximately same as entity's `created` field value
           before-now (-> now (t/minus (t/hours 5)) c/to-date)
           after-now (-> now (t/plus (t/hours 5)) c/to-date)
           within? #(apply t/within? (map c/from-date [%1 %2 %3]))]
       ;; `from` & `to` values are in turn used to compare with entity's `created` field
       (testing "sightings with specified date range"
         (let [sightings (observable->sightings {:from before-now :to after-now})]
           (is (seq sightings))
           (is (every? #(within? before-now after-now %) (map :created sightings))
               "all resulting sightings should be within given date range")
           (is (empty? (observable->sightings {:from after-now})))))
       (testing "judgements with specified date range"
         (let [judgements (observable->judgements {:from before-now :to after-now})]
           (is (seq judgements))
           (is (every? #(within? before-now after-now %) (map :created judgements))
               "all resulting judgements should be within given date range")
           (is (empty? (observable->judgements {:from after-now})))))))))
