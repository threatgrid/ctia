(ns ctia.schemas.graphql.common
  (:import graphql.Scalars))

(def lucene-query-arguments
  {:query {:type Scalars/GraphQLString
           :description (str "A Lucene query string, will only "
                             "return Relationships matching it.")
           :default "*"}})
