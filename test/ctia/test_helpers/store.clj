(ns ctia.test-helpers.store
  (:require [clojure.set :as set]
            [clojure.string :as str]
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

(def KnownStores #{(apply s/enum store/known-stores)})

(s/defn with-enabled-stores
  [enabled-stores :- KnownStores
   f :- (s/=> s/Any)]
  (helpers/with-properties ["ctia.features.disable" (str/join "," (into (sorted-set) (map name)
                                                                        (-> store/known-stores
                                                                            (disj :event :identity)
                                                                            (set/difference enabled-stores))))]
    (f)))

(s/defn test-selected-stores-with-app
  "Takes a 1-argument function which accepts a Trapperkeeper `app`
  which should succeed for the stores named in the first argument."
  ;;TODO remove me
  ([selected-stores :- #{(s/eq :es-store)}
    t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (test-selected-stores-with-app selected-stores #{} t))
  ([selected-stores :- #{(s/eq :es-store)}
    enabled-stores :- KnownStores
    t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (assert (seq selected-stores) "Empty selected-stores")
   (with-enabled-stores enabled-stores
     (fn []
       (doseq [:let [store-fixtures (select-keys store-fixtures selected-stores)
                     _ (assert (seq store-fixtures) "No stores selected")]
               [store-key fixtures] store-fixtures]
         (testing (name store-key)
           (fixtures
             (fn []
               (t (helpers/get-current-app))))))))))

(s/defn test-for-each-store-with-app
  "Takes a 1-argument function which accepts a Trapperkeeper `app`
  which should succeed for all stores."
  ;;TODO remove me
  ([t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (test-for-each-store-with-app #{} t))
  ([enabled-stores :- #{(apply s/enum store/known-stores)}
    t :- (s/=> s/Any
               (s/named s/Any 'app))]
   (test-selected-stores-with-app (-> store-fixtures keys set)
                                  enabled-stores
                                  t)))
