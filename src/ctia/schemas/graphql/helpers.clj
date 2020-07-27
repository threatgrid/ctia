(ns ctia.schemas.graphql.helpers
  (:require [clojure
             [walk :as walk :refer [stringify-keys]]]
            [schema.core :as s]
            [clojure.tools.logging :as log])
  (:import [graphql GraphQL GraphQLException]
           [graphql.language
            Field FragmentDefinition FragmentSpread NamedNode SelectionSetContainer]
           [graphql.schema
            DataFetcher
            DataFetchingEnvironment
            GraphQLArgument 
            GraphQLEnumType
            GraphQLEnumValueDefinition
            GraphQLFieldDefinition
            GraphQLFieldDefinition$Builder
            GraphQLInputObjectField
            GraphQLInputObjectType
            GraphQLInputObjectType$Builder
            GraphQLInputType
            GraphQLInterfaceType
            GraphQLList
            GraphQLNonNull
            GraphQLObjectType
            GraphQLObjectType$Builder
            GraphQLOutputType
            GraphQLSchema
            GraphQLTypeReference
            GraphQLUnionType
            TypeResolver]))

(defn resolve-with-rt-opt [maybe-fn rt-opt]
  (if (fn? maybe-fn)
    (maybe-fn rt-opt)
    maybe-fn))

;; Type registry to avoid any duplicates when using new-object
;; or new-enum. Contains a map with types indexed by name
(def default-type-registry (atom {}))

(defprotocol ConvertibleToJava
  (->java [o] "convert clojure data structure to java object"))

(extend-protocol ConvertibleToJava
  clojure.lang.IPersistentMap
  (->java [o]
    (-> o
        ^java.util.Map (stringify-keys)
        java.util.HashMap.))
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
  ([^String enum-name ^String description values registry]
   (or (get @registry enum-name)
       (let [builder (-> (GraphQLEnumType/newEnum)
                         (.name enum-name)
                         (.description description))
             names-and-values? (map? values)]
         (doseq [value values]
           (if names-and-values?
             (.value builder (key value) (val value))
             (if (string? value)
               (.value builder ^String value)
               ;unsure if reachable
               (.value builder ^GraphQLEnumValueDefinition value))))
         (let [graphql-enum (.build builder)]
           (swap! registry assoc enum-name graphql-enum)
           graphql-enum)))))

(defn list-type [t] #(GraphQLList/list (-> t (resolve-with-rt-opt %))))

(defn non-null [t] #(GraphQLNonNull/nonNull (-> t (resolve-with-rt-opt %))))

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
  (log/debug msg (pr-str value)))

(defn get-selections
  "return selections if the given entity
   is either a Field or a FragmentDefinition"
  [^SelectionSetContainer s]
  (when (or (instance? Field s)
            (instance? FragmentDefinition s))
    (some-> s .getSelectionSet .getSelections)))

(defn get-selections-get [^DataFetchingEnvironment s]
  (some-> ^DataFetchingEnvironment s .getSelectionSet .get))

(defn fragment-spread? [x]
  (instance? FragmentSpread x))

(defn fragment-definition? [x]
  (instance? FragmentDefinition x))

(defn resolve-fragment-selections
  "if the given entity is a FragmentSpread
  return its definition else return the entity"
  ^NamedNode
  [^NamedNode s fragments]
  (if (fragment-spread? s)
    (let [f-name (.getName s)]
      (get fragments (keyword f-name)))
    s))

(defn fields->selections
  "Given a fields list and a fragment def map,
  recursively pull all selections"
  [all-fields fragments]
  (loop [fields all-fields
         names []]
    (let [field (first fields)
          ;; if the field is a Fragment, replace it with its definition
          resolved (resolve-fragment-selections field fragments)
          ;; if a field has sub selections, fetch them
          selections (get-selections resolved)
          ;; compute a selection list with (when available)
          ;; field name and sub selection field names
          new-names (cond-> []
                      (and resolved
                           (not (fragment-definition? resolved)))
                      (conj (.getName resolved))
                      selections (concat (fields->selections selections fragments)))
          ;; finally distinct the selection field names
          acc (distinct (concat names new-names))]

      ;; recurr the next fields or return the list when done
      (if-let [remaining-fields (seq (rest fields))]
        (recur remaining-fields acc)
        (map keyword acc)))))

(s/defn env->field-selection :- [s/Keyword]
  "Given a DataFetchingEnvironmentImpl and a Fragment definition map
  recursively pull all selected fields as a flat sequence
  TODO: Starting from graphql-java 7.0.0 DataFetchingFieldSelectionSetImpl
  seems to return more fields but looks still insufficient,
  we should replace this function with what graphql-java
  provides whenever possible."
  [env :- DataFetchingEnvironment
   fragments :- {s/Keyword FragmentDefinition}]
  (let [selection-set (get-selections-get env)
        first-fields (keys (->clj selection-set))
        fields (mapcat (fn [[k v]] v) selection-set)
        detected-selections (fields->selections
                             (concat fields
                                     (->clj (.getFields env))) fragments)]
    (distinct
     (cond-> [:type]
       (seq first-fields) (concat first-fields)
       (seq detected-selections) (concat detected-selections)))))

(defn fn->data-fetcher
  "Converts a function that takes 4 parameters (context, args, field-selection value)
  to a GraphQL DataFetcher"
  [f rt-opt]
  (reify DataFetcher
    (get [_ env]
      (let [fragments (->clj (.getFragmentsByName env))
            context (->clj (.getContext env))
            args (->clj (.getArguments env))
            value (->clj (.getSource env))
            field-selection (env->field-selection env fragments)
            result (-> (f context args field-selection value)
                       (resolve-with-rt-opt rt-opt))]
        (debug "data-fetcher context:" context)
        (debug "data-fetcher args:" args)
        (debug "data-fetcher value:"  value)
        (debug "data-fetcher result:" result)
        result))))

(defn map-resolver
  ([k] (map-resolver k identity))
  ([k f]
   (fn [_ _ _ value]
     (when value
       (f (get value k))))))

                                        ;----- Input

(defn new-argument
  ^GraphQLArgument
  [^String arg-name
   arg-type
   ^String arg-description
   arg-default-value]
  (let [builder
        (-> (GraphQLArgument/newArgument)
            (.name arg-name)
            (.type arg-type)
            (.description (or arg-description "")))]
    (when (some? arg-default-value)
      (.defaultValue builder arg-default-value))
    (.build builder)))

(defn add-args
  ^GraphQLFieldDefinition$Builder
  [^GraphQLFieldDefinition$Builder field
   args]
  (doseq [[k {arg-type :type
              arg-description :description
              arg-default-value :default
              :or {arg-description ""}}] args]
    (let [narg
          (new-argument (name k)
                        arg-type
                        arg-description
                        arg-default-value)]
      (.argument field narg)))
  field)

(defn new-input-field
  ^GraphQLInputObjectField
  [^String field-name
   field-type
   ^String field-description
   default-value]
 #(let [^GraphQLInputType field-type (-> field-type
                                         (resolve-with-rt-opt %))
        _ (log/debug "New input field" field-name (pr-str field-type))
        builder
        (-> (GraphQLInputObjectField/newInputObjectField)
            (.name field-name)
            (.type field-type)
            (.description field-description))]
    (when (some? default-value)
      (.defaultValue builder default-value))
    (.build builder)))

(defn add-input-fields
  ^GraphQLInputObjectType$Builder
  [^GraphQLInputObjectType$Builder builder
   fields]
 (fn [rt-opt]
  (doseq [[k {field-type :type
              field-description :description
              field-default-value :default-value
              :or {field-description ""}}] fields]
    (let [newf
          (-> (new-input-field (name k)
                               field-type
                               field-description
                               field-default-value)
              (resolve-with-rt-opt rt-opt))]
      (.field builder newf)))
  builder))

(defn new-input-object
  [^String object-name
   ^String description
   fields]
 #(-> (GraphQLInputObjectType/newInputObject)
      (.name object-name)
      (.description description)
      (add-input-fields fields)
      (resolve-with-rt-opt %)
      .build))

;;----- Output

(defn new-field
  ^GraphQLFieldDefinition
  [^String field-name
   ^GraphQLOutputType field-type
   ^String field-description
   field-args
   field-data-fetcher]
  (log/debug "New field" field-name (pr-str field-type))
  (-> (GraphQLFieldDefinition/newFieldDefinition)
      (.name field-name)
      (.type field-type)
      (.description field-description)
      (.dataFetcher field-data-fetcher)
      (add-args field-args)
      .build))

(defn add-fields
  ^GraphQLObjectType$Builder
  [^GraphQLObjectType$Builder builder
   fields rt-opt]
  (doseq [[k {field-type :type
              field-description :description
              field-args :args
              field-resolver :resolve
              :or {field-description ""
                   field-args {}
                   field-resolver (map-resolver k)}}] fields]
    (let [newf
          (new-field (name k)
                     field-type
                     field-description
                     field-args
                     (fn->data-fetcher field-resolver rt-opt))]
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
  #(or (get @registry object-name)
       (let [builder (-> (GraphQLObjectType/newObject)
                         (.description description)
                         (.name object-name)
                         (add-fields fields %))]
         (doseq [^GraphQLInterfaceType interface interfaces]
           (.withInterface builder interface))
         (let [obj (.build builder)]
           (swap! registry assoc object-name obj)
           obj)))))

(defn fn->type-resolver
  "Converts a function that takes the current object, the args
  and the global schema to a TypeResolver."
  ^TypeResolver
  [f rt-opt]
  (reify TypeResolver
    (getType [_ env]
      (let [object (->clj (.getObject env))
            args (->clj (.getArguments env))
            schema (.getSchema env)]
        (-> (f object args schema)
            (resolve-with-rt-opt rt-opt))))))

(defn new-union
  [^String union-name
   ^String description
   type-resolver-fn
   types]
 #(let [type-resolver (fn->type-resolver type-resolver-fn %)
        graphql-union (-> (GraphQLUnionType/newUnionType)
                          (.description description)
                          (.name union-name)
                          ; FIXME: this method is deprecated
                          (.typeResolver type-resolver))]
    (doseq [type types]
      (if (instance? GraphQLObjectType type)
        (.possibleType graphql-union ^GraphQLObjectType type)
        (.possibleType graphql-union ^GraphQLTypeReference type)))
    (.build graphql-union)))

(defn new-ref
  [object-name]
  (GraphQLTypeReference. object-name))

(defn new-schema
  [^GraphQLObjectType query]
 #(-> (GraphQLSchema/newSchema)
      (.query ^GraphQLObjectType (resolve-with-rt-opt query %))
      .build))

(defn get-type
  "Retrieves a Type from the given schema by its name"
  [^GraphQLSchema schema
   type-name]
  (.getType schema type-name))

(defn new-graphql
  [schema]
 #(-> (GraphQL/newGraphQL (-> schema (resolve-with-rt-opt %)))
      .build))

(defn execute
  [^GraphQL graphql
   query
   operation-name
   variables
   context]
  (try
    (let [result (.execute graphql
                           query
                           operation-name
                           (->java (or context {}))
                           (->java (or variables {})))]
      {:data (->clj (.getData result))
       :errors (.getErrors result)})
    (catch GraphQLException e
      (log/error e)
      {:errors [(.getMessage e)]})))
