(ns ctia.schemas.graphql.common
  (:require [ctia.schemas.graphql.scalars :refer [GraphQLDate]])
  (:import graphql.Scalars))

(def lucene-query-arguments
  {:query {:type Scalars/GraphQLString
           :description (str "A Lucene query string, will only "
                             "return nodes matching it.")
           :default "*"}})

(def time-metadata-fields
  "default internal time metadata fields"
  {:created  {:type GraphQLDate
              :description "CTIA Record creation date"}
   :modified  {:type GraphQLDate
               :description "CTIA Record last modification date"}})
