(ns ctia.observable.core-test
  (:require [ctia.observable.core :as sut]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
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
           bundle (fixt/sightings-threat-ctx-bundle 2 true)
           observable (-> bundle :sightings first :observables first)
           bundle-res (helpers/POST-bulk app bundle)
           identity-map {:login "foouser" :groups ["foogroup"]}]
       (is (= (set (:incidents bundle-res))
              (set (sut/sighting-observable->incident-ids observable
                                                          identity-map
                                                          services))))
       (is (= (set (:indicators bundle-res))
              (set (sut/sighting-observable->indicator-ids observable
                                                           identity-map
                                                           services))))
       (is (= (set (:judgments bundle-res))
              (set (sut/judgement-observable->indicator-ids observable
                                                            identity-map
                                                            services))))))))