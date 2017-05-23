(ns ctia.schemas.graphql.helpers
  (:require [clojure
             [string :as str]
             [walk :refer [stringify-keys]]]
            [clojure.tools.logging :as log])
  (:import graphql.GraphQL
           (graphql.schema DataFetcher
                           GraphQLArgument
                           GraphQLEnumType
                           GraphQLFieldDefinition
                           GraphQLList
                           GraphQLNonNull
                           GraphQLObjectType
                           GraphQLSchema
                           GraphQLTypeReference)))

(defprotocol ConvertibleToJava
  (->java [o] "convert clojure data structure to java object"))

(extend-protocol ConvertibleToJava
  clojure.lang.IPersistentMap
  (->java [o]
    (-> o
        stringify-keys
        (java.util.HashMap.)))
  nil
  (->java [_] nil))

(defprotocol ConvertibleToClojure
  "Helper to convert some of the generic data types graphql-java uses
  into more natural clojure forms."
  (->clj [o] "convert Java object to natural clojure form"))

(extend-protocol ConvertibleToClojure
  java.util.Map
  (->clj [o]
    (let [entries (.entrySet o)]
      (reduce (fn [m [^String k v]]
                (assoc m (keyword k) (->clj v)))
              {}
              entries)))

  java.util.List
  (->clj [o] (vec (map ->clj o)))

  java.lang.Object
  (->clj [o] o)

  nil
  (->clj [_] nil))

(defn- escape-enum-value-name
  [enum-value-name]
  (str/replace enum-value-name #"[-]" "_"))

(defn enum
  [enum-name description c]
  (let [graphql-enum (-> (GraphQLEnumType/newEnum)
                         (.name enum-name)
                         (.description description))]
    (doseq [value c]
      (.value graphql-enum
              (escape-enum-value-name value)
              value))
    (.build graphql-enum)))

(defn list-type
  [t]
  (GraphQLList/list t))

(defn non-null
  [t]
  (GraphQLNonNull/nonNull t))

(defn non-nulls
  [m]
  (into
   {}
   (map (fn [[k {elt-type :type :as v}]]
          [k (assoc v :type (non-null elt-type))])
        m)))

(defn fn->data-fetcher
  [f]
  (reify DataFetcher
    (get [_ env]
      (let [context (->clj (.getContext env))
            args (->clj (.getArguments env))
            value (->clj (.getSource env))
            result (f context args value)]
        (log/debug "data-fetcher context:" context)
        (log/debug "data-fetcher args:" args)
        (log/debug "data-fetcher value:" value)
        (log/debug "data-fetcher result:" result)
        result))))

(defn map-resolver
  ([k] (map-resolver k identity))
  ([k f]
   (fn [_ _ value]
     (when value
       (f (get value k))))))

(defn new-argument
  [arg-name
   arg-type
   arg-description
   arg-default-value]
  (let [new-arg (-> (GraphQLArgument/newArgument)
                    (.name arg-name)
                    (.type arg-type)
                    (.description (or arg-description "")))]
    (when (some? arg-default-value)
      (.defaultValue new-arg arg-default-value))
    new-arg))

(defn add-args
  [field args]
  (doseq [[k {arg-type :type
              arg-description :description
              arg-default-value :default
              :or {arg-description ""}}] args]
    (.argument field (new-argument (name k)
                                   arg-type
                                   arg-description
                                   arg-default-value)))
  field)

(defn new-field
  [field-name
   field-type
   field-description
   field-args
   field-data-fetcher]
  (-> (GraphQLFieldDefinition/newFieldDefinition)
      (.name field-name)
      (.type field-type)
      (.description field-description)
      (.dataFetcher field-data-fetcher)
      (add-args field-args)))

(defn add-fields
  [t fields]
  (doseq [[k {field-type :type
              field-description :description
              field-args :args
              field-resolver :resolve
              :or {field-description ""
                   field-args {}
                   field-resolver (map-resolver k)}}] fields]
    (.field t (new-field (name k)
                         field-type
                         field-description
                         field-args
                         (fn->data-fetcher field-resolver))))
  t)

(defn new-object
  [object-name description interfaces fields]
  (let [graphql-object (-> (GraphQLObjectType/newObject)
                           (.description description)
                           (.name object-name))]
    (doseq [interface interfaces]
      (.withInterface graphql-object interface))
    (-> graphql-object
        (add-fields fields)
        (.build))))

(defn new-ref
  [object-name]
  (new GraphQLTypeReference object-name))

(defn new-schema
  [query]
  (-> (GraphQLSchema/newSchema)
      (.query query)
      (.build)))

(defn new-graphql
  [schema]
  (-> (GraphQL/newGraphQL schema)
      (.build)))

(defn execute [graphql query variables]
  (let [result (.execute graphql query nil (->java variables))]
    {:data (.getData result)
     :errors (.getErrors result)}))

