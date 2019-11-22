(ns ctia.schemas.graphql.ownership
  (:require [ctia.schemas.graphql.helpers :as g])
  (:import graphql.Scalars))

(def graphql-ownership-fields
  "default document ownership for all CTIA records"
  {:owner {:type Scalars/GraphQLString
           :description "CTIA Record Owner"}
   :groups {:type (g/list-type Scalars/GraphQLString)
            :description "CTIA Record Groups"}})
