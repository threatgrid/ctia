(ns ctia.schemas.graphql.judgement
  (:require [ctia.domain.entities :as ent]
            [ctia.domain.entities.judgement :refer [with-long-id]]
            [ctia.schemas.graphql.common :as c]
            [ctia.schemas.graphql.observable :as o]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.store :refer :all])
  (:import graphql.Scalars))

(def judgement-fields
  (merge c/base-entity-fields
         c/sourced-object-entries
         (g/non-nulls
          {:observable {:type o/ObservableType}
           :disposition {:type c/DispositionNumberType}
           :disposition_name {:type c/DispositionNameType}
           :priority
           {:type Scalars/GraphQLInt
            :description "A value 0-100 that determine the priority of a judgement."}
           :confidence {:type c/HighMedLow}
           :severity {:type c/HighMedLow}
           :valid_time {:type c/ValidTime}})
         {:reason {:type (Scalars/GraphQLString)}
          :reason_uri {:type (Scalars/GraphQLString)}}))

(def judgement-type-name "Judgement")

(def JudgementType
  (g/new-object judgement-type-name
                "A judgement about the intent or nature of an observable."
                []
                judgement-fields))

(defn judgement-by-id
  [id]
  (some-> (read-store :judgement read-judgement id)
          with-long-id
          ent/un-store))
