(ns ctia.test-helpers.store
  (:require
    [clojure.test :refer [join-fixtures]]
    [ctia.test-helpers.atom :as at-helpers]
    [ctia.test-helpers.core :as helpers]
    [ctia.test-helpers.es :as es-helpers]))

(defmacro deftest-for-each-store [test-name & body]
  `(helpers/deftest-for-each-fixture ~test-name
     {:atom-memory-store (join-fixtures [at-helpers/fixture-properties:atom-memory-store
                                         helpers/fixture-ctia])

      :atom-durable-store (join-fixtures [at-helpers/fixture-properties:atom-durable-store
                                          helpers/fixture-ctia
                                          at-helpers/fixture-reset-stores])

      :es-store   (join-fixtures [es-helpers/fixture-properties:es-store
                                  helpers/fixture-ctia
                                  es-helpers/fixture-recreate-store-indexes])

      :es-store-native (join-fixtures [es-helpers/fixture-properties:es-store-native
                                       helpers/fixture-ctia
                                       es-helpers/fixture-recreate-store-indexes])


      :multi-store (join-fixtures [helpers/fixture-properties:multi-store
                                   helpers/fixture-ctia
                                   es-helpers/fixture-recreate-store-indexes])}
     ~@body))
