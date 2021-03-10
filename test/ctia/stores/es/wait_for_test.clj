(ns ctia.stores.es.wait-for-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-momo.test-helpers.core :as mth]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.entity.incident :refer [incident-entity]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.crud :refer [crud-wait-for-test]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.store :refer [test-selected-stores-with-app]]
            [ctim.examples.incidents :refer [new-incident-maximal]]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once
              mth/fixture-schema-validation
              whoami-helpers/fixture-server)

;; we choose incidents to test wait_for because it supports patches and
;; thus achieves full coverage of crud-wait-for-test
(deftest test-wait_for
  (test-selected-stores-with-app
    #{:es-store}
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
      (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (let [{{:keys [get-in-config]} :ConfigService} (app/service-graph app)
            {:keys [entity] :as parameters} (into incident-entity
                                                  {:app app
                                                   :example new-incident-maximal
                                                   :headers {:Authorization "45c1f5e3f05d0"}})]
        (is (= "es" (get-in-config [:ctia :store entity])))
        (crud-wait-for-test parameters)))))
