(ns ctia.observable.core-test
  (:require [ctia.observable.core :as sut]
            [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
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
           gen-incident-bundle #(fixt/incident-threat-ctx-bundle {:nb-sightings 20
                                                                  :maximal? true})
           bundle (apply merge-with concat
                         (repeatedly 3 gen-incident-bundle))
           sighting-observable (-> bundle :sightings first :observables first)
           judgement-observable (-> bundle :judgements first :observable)
           bundle-res (helpers/POST-bulk app bundle)
           identity-map {:login "foouser" :groups ["foogroup"]}
           incidents (:incidents bundle-res)
           indicators (:indicators bundle-res)]
       (assert (seq incidents))
       (assert (seq indicators))
       (is (= (set incidents)
              (set
               (:data (sut/sighting-observable->incident-ids sighting-observable
                                                             identity-map
                                                             services)))))
       (is (= (set indicators)
              (set
               (:data
                (sut/judgement-observable->indicator-ids judgement-observable
                                                         identity-map
                                                         services)))))
       (testing "sighting-observable->indicator-ids returns all indicators ids and can be crawled."
         (let [get-indicator-ids
               (fn [observable
                    nb-page
                    limit]
                 (let [read-page
                       (fn [paging-params]
                         (sut/sighting-observable->indicator-ids observable
                                                                 paging-params
                                                                 identity-map
                                                                 services))]
                   (loop [nb nb-page
                          p {:limit limit}
                          ids []]
                     (if (pos? nb)
                       (let [{:keys [data paging]} (read-page p)]
                         (println "paging res")
                         (clojure.pprint/pprint paging)
                         (recur (dec nb) (:next paging) (concat ids data)))
                       (set ids)))))]
           (is (= (set indicators) (get-indicator-ids sighting-observable 3 6)))
           (testing "\n[[s1 r1 i1] [s1 r2 i2]] and limit 1 must not search_after s1 for second page."
             (let [obs {:type "domain" :value "source-edge-case.com"}
                   sightings (map #(assoc % :observables [obs])
                                  (fixt/n-examples :sighting 1  true))
                   indicators (fixt/n-examples :indicator 2 false)
                   relationships (fixt/mk-relationships "sighting-of" sightings indicators)
                   bundle {:sightings sightings
                           :indicators indicators
                           :relationships relationships}
                   expected-ids (:indicators (helpers/POST-bulk app bundle))]
               (assert (= 2 (count expected-ids)))
               (is (= 1 (count (get-indicator-ids obs 1 1))))
               (is (= (set expected-ids) (get-indicator-ids obs 3 1)))
               ))
           )))
       )))

(deftest encode-decode-paging
  (let [paging {:sighting
                {:limit 6,
                 :offset 6,
                 :search_after ["sighting-39dace73-3fec-444b-a6f0-fb9aa78ec8d1"]},
                :relationship
                {:limit 6,
                 :offset 6,
                 :search_after
                 ["http://localhost:59282/ctia/indicator/indicator-ef7a8030-3879-4ffe-8ff2-fd4bc9bbbfae"]}}
        encoded (sut/encode-paging paging)]
    (is (string? encoded))
    (is (= paging (sut/decode-paging encoded)))))
