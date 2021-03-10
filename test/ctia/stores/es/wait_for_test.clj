(ns ctia.stores.es.wait-for-test
  (:require [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [crud-wait-for-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-selected-stores-with-app]]

            [ctia.entity.incident :refer [incident-entity]]
            [ctim.examples.incidents
             :refer
             [new-incident-maximal new-incident-minimal]]))

(def new-judgement
  (merge ex/new-judgement-maximal
         {:observable {:value "1.2.3.4"
                       :type "ip"}
          :external_ids ["http://ex.tld/ctia/judgement/judgement-123"
                         "http://ex.tld/ctia/judgement/judgement-456"]
          :disposition 2
          :disposition_name "Malicious"
          :source "test"
          :priority 100
          :severity "High"
          :confidence "Low"
          :reason "This is a bad IP address that talked to some evil servers"}))

;; we choose incidents to test wait_for because it supports patches and
;; thus achieves full coverage of crud-wait-for-test
(deftest test-wait_for
  (let [ran? (atom false)]
    (test-selected-stores-with-app
      (fn [app]
        (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
        (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
        (let [{:keys [entity] :as parameters} (into incident-entity
                                                    {:app app
                                                     :example new-incident-maximal
                                                     :headers {:Authorization "45c1f5e3f05d0"}})]
          (when (= "es"
                   (get-in-config [:ctia :store entity]))
            (crud-wait-for-test parameters)
            (reset! ran? true)))))
    (is @ran?)))
