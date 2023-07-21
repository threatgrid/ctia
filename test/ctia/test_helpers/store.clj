(ns ctia.test-helpers.store
  (:require [clojure.string :as str]
            [clojure.test :refer [join-fixtures testing]]
            [ctia.store :as store]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [schema.core :as s]))

(def store-fixtures
  {:es-store
   (join-fixtures [es-helpers/fixture-properties:es-store
                   helpers/fixture-ctia
                   es-helpers/fixture-delete-store-indexes])})

(s/defn test-selected-stores-with-app
  "Takes a 1-argument function which accepts a Trapperkeeper `app`
  which should succeed for the stores named in the first argument."
  [selected-stores :- #{s/Keyword}
   t :- (s/=> s/Any
              (s/named s/Any 'app))]
  (assert (seq selected-stores) "Empty selected-stores")
  (doseq [:let [store-fixtures (select-keys store-fixtures selected-stores)
                _ (assert (seq store-fixtures) "No stores selected")]
          [store-key fixtures] store-fixtures]
    (testing (name store-key)
      (fixtures
        (fn []
          (t (helpers/get-current-app)))))))

(s/defn test-for-each-store-with-app
  "Takes a 1-argument function `t` which accepts a Trapperkeeper `app`
  which should succeed for all stores. `enabled-stores` is a set of
  store keywords (such as :incident, :actor, etc.) to initialize for
  this store. Only specify stores actually used by the test so the
  test may skip unnecessary initialization."
  ;;TODO delete this arity to force optimized tests
  ([t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (test-selected-stores-with-app
     (-> store-fixtures keys set)
     t))
  ([enabled-stores :- #{(apply s/enum store/known-stores)}
    t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (helpers/with-properties
     ["ctia.features.disable" (str/join "," (map name enabled-stores))]
     (test-selected-stores-with-app
       (-> store-fixtures keys set)
       t))))
