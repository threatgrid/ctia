(ns ctia.schemas.graphql.helpers
  (:require [clojure
             [string :as str]
             [walk :refer [stringify-keys]]]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk])
  (:import graphql.GraphQL
           [graphql.schema
            DataFetcher GraphQLArgument GraphQLArgument$Builder GraphQLEnumType
            GraphQLFieldDefinition GraphQLInputObjectType
            GraphQLInputObjectType$Builder GraphQLInputObjectField
            GraphQLInputObjectField$Builder GraphQLInputType
            GraphQLInterfaceType GraphQLList GraphQLNonNull GraphQLObjectType
            GraphQLObjectType$Builder GraphQLOutputType GraphQLSchema
            GraphQLTypeReference GraphQLUnionType TypeResolver]))

;; Type registry to avoid any duplicates when using new-object
;; or new-enum. Contains a map with types indexed by name
(def default-type-registry (atom {}))

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

(defn valid-type-name?
  "A GraphQL Type Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]*"
  [n]
  (some?
   (when (and n (not (empty? n)))
     (re-matches #"[_A-Za-z][_0-9A-Za-z]*" n))))

(defn valid-type-names?
  [c]
  (every? valid-type-name? c))

(defn enum
  "Creates a GraphQLEnumType. If a type with the same name has already been
   created, the corresponding object is retrieved from the provided or the
   default type repository."
  ([enum-name description values] (enum enum-name
                                   description
                                   values
                                   default-type-registry))
  ([enum-name description values registry]
   (or (get @registry enum-name)
       (let [builder (-> (GraphQLEnumType/newEnum)
                         (.name enum-name)
                         (.description description))
             names-and-values? (map? values)]
         (doseq [value values]
           (if names-and-values?
             (.value builder (key value) (val value))
             (.value builder value)))
         (let [graphql-enum (.build builder)]
           (swap! registry assoc enum-name graphql-enum)
           graphql-enum)))))

(defn list-type [t] (GraphQLList/list t))

(defn non-null [t] (GraphQLNonNull/nonNull t))

(defn non-nulls
  "Takes a map containing GraphQL fields and decorates them
  with the GraphQLNonNull wrapper."
  [m]
  (into
   {}
   (map (fn [[k {elt-type :type :as v}]]
          [k (assoc v :type (non-null elt-type))])
        m)))

(defn debug
  [msg value]
  (log/debugf msg (pr-str value)))

(defn fn->data-fetcher
  "Converts a function that takes 3 parameters (context, args and value)
  to a GraphQL DataFetcher"
  [f]
  (reify DataFetcher
    (get [_ env]
      (let [context (->clj (.getContext env))
            args (->clj (.getArguments env))
            value (->clj (.getSource env))
            result (f context args value)]
        (debug "data-fetcher context:" context)
        (debug "data-fetcher args:" args)
        (debug "data-fetcher value:"  value)
        (debug "data-fetcher result:" result)
        result))))

(defn map-resolver
  ([k] (map-resolver k identity))
  ([k f]
   (fn [_ _ value]
     (when value
       (f (get value k))))))

;----- Input

(defn new-argument
  [^String arg-name
   ^GraphQLOutputType arg-type
   ^String arg-description
   ^Object arg-default-value]
  (let [^GraphQLArgument$Builder builder
        (-> (GraphQLArgument/newArgument)
            (.name arg-name)
            (.type arg-type)
            (.description (or arg-description "")))]
    (when (some? arg-default-value)
      (.defaultValue builder arg-default-value))
    (.build builder)))

(defn add-args
  [^GraphQLFieldDefinition field
   args]
  (doseq [[k {arg-type :type
              arg-description :description
              arg-default-value :default
              :or {arg-description ""}}] args]
    (let [^GraphQLArgument narg
          (new-argument (name k)
                        arg-type
                        arg-description
                        arg-default-value)]
      (.argument field narg)))
  field)

(defn new-input-field
  [^String field-name
   ^GraphQLInputType field-type
   ^String field-description
   ^Object default-value]
  (log/debug "New input field" field-name (pr-str field-type))
  (let [^GraphQLInputObjectField$Builder builder
        (-> (GraphQLInputObjectField/newInputObjectField)
            (.name field-name)
            (.type field-type)
            (.description field-description))]
    (when (some? default-value)
      (.defaultValue builder default-value))
    (.build builder)))

(defn add-input-fields
  [^GraphQLInputObjectType$Builder builder
   fields]
  (doseq [[k {field-type :type
              field-description :description
              field-default-value :default-value
              :or {field-description ""}}] fields]
    (let [^GraphQLInputObjectField newf
          (new-input-field (name k)
                           field-type
                           field-description
                           field-default-value)]
      (.field builder newf)))
  builder)

(defn new-input-object
  [^String object-name
   ^String description
   fields]
  (-> (GraphQLInputObjectType/newInputObject)
      (.name object-name)
      (.description description)
      (add-input-fields fields)
      (.build)))

;;----- Ouput

(defn new-field
  [^String field-name
   ^GraphQLOutputType field-type
   ^String field-description
   field-args
   ^DataFetcher field-data-fetcher]
  (log/debug "New field" field-name (pr-str field-type))
  (-> (GraphQLFieldDefinition/newFieldDefinition)
      (.name field-name)
      (.type field-type)
      (.description field-description)
      (.dataFetcher field-data-fetcher)
      (add-args field-args)
      (.build)))

(defn add-fields
  [^GraphQLObjectType$Builder builder
   fields]
  (doseq [[k {field-type :type
              field-description :description
              field-args :args
              field-resolver :resolve
              :or {field-description ""
                   field-args {}
                   field-resolver (map-resolver k)}}] fields]
    (let [^GraphQLFieldDefinition newf
          (new-field (name k)
                     field-type
                     field-description
                     field-args
                     (fn->data-fetcher field-resolver))]
      (.field builder newf)))
  builder)

(defn new-object
  "Creates a GraphQLObjectType. If a type with the same name has already been
   created, the corresponding object is retrieved from the provided or the
   default type repository."
  ([object-name description interfaces fields]
   (new-object object-name description interfaces fields default-type-registry))
  ([^String object-name
    ^String description
    interfaces
    fields
    registry]
   (or (get @registry object-name)
       (let [builder (-> (GraphQLObjectType/newObject)
                         (.description description)
                         (.name object-name)
                         (add-fields fields))]
         (doseq [^GraphQLInterfaceType interface interfaces]
           (.withInterface builder interface))
         (let [obj (.build builder)]
           (swap! registry assoc object-name obj)
           obj)))))

(defn fn->type-resolver
  "Converts a function that takes the current object, the args
  and the global schema to a TypeResolver."
  [f]
  (reify TypeResolver
    (getType [_ env]
      (let [object (->clj (.getObject env))
            args (->clj (.getArguments env))
            schema (.getSchema env)]
        (f object args schema)))))

(defn new-union
  [^String union-name
   ^String description
   type-resolver-fn
   types]
  (let [^TypeResolver type-resolver
        (fn->type-resolver type-resolver-fn)
        ^GraphQLUnionType
        graphql-union (-> (GraphQLUnionType/newUnionType)
                          (.description description)
                          (.name union-name)
                          (.typeResolver type-resolver))]
    (doseq [type types]
      (.possibleType graphql-union type))
    (.build graphql-union)))

(defn new-ref
  [^String object-name]
  (new GraphQLTypeReference object-name))

(defn new-schema
  [^GraphQLObjectType query]
  (-> (GraphQLSchema/newSchema)
      (.query query)
      (.build)))

(defn get-type
  "Retrieves a Type from the given schema by its name"
  [^GraphQLSchema schema
   ^String type-name]
  (.getType schema type-name))

(defn new-graphql
  [^GraphQLSchema schema]
  (-> (GraphQL/newGraphQL schema)
      (.build)))

(defn execute
  [^GraphQL graphql
   ^String query
   ^String operation-name
   ^java.util.Map variables]
  (let [result (.execute graphql
                         query
                         operation-name
                         nil
                         (->java (or variables {})))]
    {:data (->clj (.getData result))
     :errors (.getErrors result)}))
