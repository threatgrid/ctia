(ns ctia.test-helpers.store
  (:require [clojure.test :refer [join-fixtures testing]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [schema.core :as s]))

(def store-fixtures
  {:es-store
   (join-fixtures [es-helpers/fixture-properties:es-store
                   helpers/fixture-ctia
                   es-helpers/fixture-delete-store-indexes])})

(s/defn test-for-each-store-with-app
  "Takes a 1-argument function which accepts a Trapperkeeper `app`
  which should succeed for all stores."
  [t :- (s/=> s/Any
              (s/named s/Any 'app))]
  (doseq [[store-key fixtures] store-fixtures]
    (testing (name store-key)
      (fixtures
        (fn []
          (t (helpers/get-current-app)))))))
