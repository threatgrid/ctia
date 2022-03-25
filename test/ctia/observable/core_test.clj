(ns ctia.observable.core-test
  (:require [ctia.observable.core :as sut]
            [clojure.test :refer [deftest is join-fixtures use-fixtures]]
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
           nb-sightings 20
           gen-incident-bundle #(fixt/incident-threat-ctx-bundle nb-sightings true)
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
      ;; (is (= (set indicators)
      ;;        (set
      ;;         (:data
      ;;          (sut/sighting-observable->indicator-ids sighting-observable
      ;;                                                  identity-map
      ;;                                                  services)))))
       (is (= (set indicators)
              (set
               (:data
                (sut/judgement-observable->indicator-ids judgement-observable
                                                         identity-map
                                                         services)))))
       (let [;;{paging :paging :as first-page}
             ;;(sut/sighting-observable->indicator-ids sighting-observable
             ;;                                        {:limit 20}
             ;;                                        identity-map
             ;;                                        services)
             ;;_ (println "second-paging: ")
             ;;_ (clojure.pprint/pprint paging)
             read-page
             (fn [paging-params]
               (println "read-page")
               (clojure.pprint/pprint paging-params)
               (sut/sighting-observable->indicator-ids sighting-observable
                                                       paging-params
                                                       identity-map
                                                       services))
             read-indicators ;; WARNING: defining schema output breaks lazyness
             (fn walk-graph [paging-params]
               (lazy-seq
                (let [{:keys [data paging]} (read-page paging-params)]
                  (when (seq data)
                    (cons data (walk-graph paging))))))
           ;;  second-page (sut/sighting-observable->indicator-ids sighting-observable
           ;;                                                      {:limit 5
           ;;                                                       :paging paging}
           ;;                                                      identity-map
           ;;                                                      services)

             indicator-ids
             (loop [nb 10
                    p {:limit 5}
                    data []]
               (println "loop " nb)
               (clojure.pprint/pprint data)
               (if (pos? nb)
                 (let [res (read-page p)]
                   (recur (dec nb) (:paging res) (concat data (:data res))))
                 (set data)))
             ]
         (println "all indicators")
         (clojure.pprint/pprint indicator-ids)
;;         (clojure.pprint/pprint (read-indicators {:limit 1}))
         (is (= (set indicators)
                indicator-ids)))
       ))))
