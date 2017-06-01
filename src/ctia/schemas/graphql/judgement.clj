(ns ctia.schemas.graphql.judgement
  (:require [ctia.schemas.graphql.common :as c]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.observable :as o]
            [ctia.schemas.graphql.pagination :as p]
            [ctia.schemas.graphql.refs :as refs]
            [ctia.schemas.graphql.relationship :as r])
  (:import graphql.Scalars))

(def judgement-fields
  (merge c/base-entity-fields
         c/sourced-object-fields
         r/relatable-entity-fields
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

(def JudgementType
  (g/new-object refs/judgement-type-name
                "A judgement about the intent or nature of an observable."
                []
                judgement-fields))

(def JudgementConnectionType
  (p/new-connection JudgementType))
