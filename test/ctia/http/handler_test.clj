(ns ctia.http.handler-test
  (:refer-clojure :exclude [get])
  (:require [ctia.http.handler :as handler]
            [ctia.test-helpers.core :refer [delete get post put] :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.store :refer [deftest-for-each-store]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)
