(ns ctia.test-helpers.store
  (:require [clojure.test :refer [join-fixtures testing]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]))

(def store-fixtures
  {:es-store
   (join-fixtures [es-helpers/fixture-properties:es-store
                   helpers/fixture-ctia
                   es-helpers/fixture-delete-store-indexes])})

(defn test-for-each-store-with-app [t]
  (doseq [[store-key fixtures] store-fixtures]
    (testing (name store-key)
      (fixtures
        (fn []
          (t (helpers/get-current-app)))))))
