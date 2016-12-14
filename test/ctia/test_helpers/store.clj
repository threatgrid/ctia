(ns ctia.test-helpers.store
  (:require
    [clojure.test :refer [join-fixtures]]
    [ctia.test-helpers.core :as helpers]
    [ctia.test-helpers.es :as es-helpers]))

(defmacro deftest-for-each-store [test-name & body]
  `(helpers/deftest-for-each-fixture ~test-name
     {:es-store   (join-fixtures [es-helpers/fixture-properties:es-store
                                  helpers/fixture-ctia
                                  es-helpers/fixture-delete-store-indexes])}
     ~@body))
