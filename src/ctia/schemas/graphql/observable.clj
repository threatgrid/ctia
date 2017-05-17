(ns ctia.schemas.graphql.observable
  (:require [ctia.domain.entities
             [judgement :as judgement]]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.store :refer :all]
            [ctia.schemas.graphql.common :as c])
  (:import graphql.Scalars))

(def observable-type-name "Observable")
(def ObservableTypeRef (g/new-ref observable-type-name))

(def verdict-fields
  (into
   (g/non-nulls
    {:type {:type Scalars/GraphQLString}
     :disposition {:type c/DispositionNumberType}
     :observable {:type ObservableTypeRef}
     :valid_time {:type c/ValidTime}})
   {:judgement_id {:type Scalars/GraphQLString}
    :disposition_name {:type c/DispositionNameType}}))

(def verdict-type-name "Verdict")

(def VerdictType
  (g/new-object
   verdict-type-name
   (str "A Verdict is chosen from all of the Judgements on that Observable "
        "which have not yet expired.")
   []
   verdict-fields))

(defn observable-verdict
  [{observable-type :type
    observable-value :value}]
  (some-> (read-store :judgement
                      calculate-verdict
                      {:type observable-type :value observable-value})
          (update :judgement_id judgement/short-id->long-id)))

;; enum can not be used for the observable type.
;; - is not a valid character for an enum value.
(def observable-fields
  {:value {:type (g/non-null Scalars/GraphQLString)}
   :type {:type (g/non-null Scalars/GraphQLString)}
   :verdict {:type VerdictType
             :resolve (fn [_ _ value]
                        (observable-verdict value))}})

(def ObservableType
  (g/new-object observable-type-name
                (str "A simple, atomic value which has a consistent identity, "
                     "and is stable enough to be attributed an intent or nature.")
                []
                observable-fields))
