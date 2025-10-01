(ns ctia.schemas.graphql.scalars
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce])
  (:import [graphql.schema GraphQLScalarType Coercing]))

;; GraphQLDate Scalar type
(def GraphQLDate
  (-> (GraphQLScalarType/newScalar)
      (.name "DATE")
      (.description "Date")
      (.coercing (reify Coercing
                   (serialize [_ ^Object input]
                     (-> input
                         time-coerce/from-date
                         time-coerce/to-date))
                   (parseValue [this ^Object input]
                     (.serialize this input))
                   (parseLiteral [this ^Object input]
                     (.serialize this input))))
      (.build)))

