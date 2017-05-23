(ns ctia.schemas.graphql.pagination
  (:require [clojure.string :as str]
            [ctia.schemas.graphql.helpers :as g])
  (:import graphql.Scalars))

(def PageInfo
  (g/new-object
   "PageInfo"
   ""
   []
   {:hasNextPage {:type (g/non-null Scalars/GraphQLBoolean)}
    :hasPreviousPage {:type (g/non-null Scalars/GraphQLBoolean)}
    :startCursor {:type Scalars/GraphQLString}
    :endCursor {:type Scalars/GraphQLString}}))

(def connection-arguments
  {:after {:type Scalars/GraphQLString}
   :first {:type Scalars/GraphQLInt
           :default 50}
   :before {:type Scalars/GraphQLString}
   :last {:type Scalars/GraphQLInt}})

(defn new-edge
  [node-type edge-name]
  (g/new-object
   edge-name
   "An edge in a connection."
   []
   {:node {:type node-type}
    :cursor {:type (g/non-null Scalars/GraphQLString)}}))

(defn new-connection
  [node-type list-name]
  (let [capitalized-list-name (str/capitalize list-name)
        connection-name (str capitalized-list-name
                             "Connection")
        edge-name (str capitalized-list-name
                       "Edge")]
    (g/new-object
     connection-name
     (str "A connection to a list of " capitalized-list-name)
     []
     (assoc {:pageInfo {:type (g/non-null PageInfo)}
             :totalCount {:type Scalars/GraphQLInt}
             :edges {:type (g/list-type (new-edge node-type
                                                  edge-name))}}
            (keyword (str/lower-case list-name))
            {:type (g/list-type node-type)}))))
