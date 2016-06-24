(ns ctia.test-helpers.store
  (:require
   [clojure.test :refer [join-fixtures]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.db :as db-helpers]
   [ctia.test-helpers.es :as es-helpers]))

(defmacro deftest-for-each-store [test-name & body]
  `(helpers/deftest-for-each-fixture ~test-name
     {:atom-store (join-fixtures [helpers/fixture-properties:atom-store
                                  helpers/fixture-ctia])

      ;; disabling until CTIM is settled
      ;;:sql-store
      ;; (join-fixtures [db-helpers/fixture-properties:sql-store
      ;;                 helpers/fixture-ctia
      ;;                 db-helpers/fixture-db-recreate-tables])

      :es-store   (join-fixtures [es-helpers/fixture-properties:es-store
                                  helpers/fixture-ctia
                                  es-helpers/fixture-recreate-store-indexes])
      :es-store-native (join-fixtures [es-helpers/fixture-properties:es-store-native
                                       helpers/fixture-ctia
                                       es-helpers/fixture-recreate-store-indexes])


      :multi-store (join-fixtures [helpers/fixture-properties:multi-store
                                   helpers/fixture-ctia
                                   ;;db-helpers/fixture-db-recreate-tables
                                   es-helpers/fixture-recreate-store-indexes])}
     ~@body))
