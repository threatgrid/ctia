(ns ctia.schemas.graphql.scalars
  (:require [clj-momo.lib.clj-time.coerce :as time-coerce])
  (:import [graphql.schema GraphQLScalarType Coercing]))

;; GraphQLDate Scalar type
(def GraphQLDate
  (GraphQLScalarType.
   "DATE"
   "Date"
   (reify Coercing
     (serialize [_ ^Object input]
       (-> input
           time-coerce/from-date
           time-coerce/to-date))
     (parseValue [^Coercing this ^Object input]
       (.serialize this input))
     (parseLiteral [^Coercing this ^Object input]
       (.serialize this input)))))

