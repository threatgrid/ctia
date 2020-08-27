(ns ctia.schemas.graphql.helpers
  (:require [clojure
             [walk :as walk :refer [stringify-keys]]]
            [schema.core :as s]
            [schema-tools.core :as st]
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

(s/defn delayed-graphql-value?
  "A flat predicate deciding if the argument is delayed."
  [v #_#_:- AnyMaybeDelayedGraphQLValue]
  (fn? v))

(s/defn resolved-graphql-value?
  "A flat predicate deciding if the argument is not delayed."
  [v #_#_:- AnyMaybeDelayedGraphQLValue]
  (not (delayed-graphql-value? v)))

(s/defschema GraphQLValue
  (s/pred
    resolved-graphql-value?))

(s/defschema GraphQLRuntimeOptions
  "A map of options to resolve a DelayedGraphQLValue."
  {:services {:ConfigService {:get-in-config (s/pred ifn?)}
              :GraphQLService {;; not a real service method, mostly internal to GraphQLService
                               :get-or-update-type-registry (s/pred ifn?)}
              :StoreService {:read-store (s/pred ifn?)}}})

(s/defn DelayedGraphQLValue
  :- (s/protocol s/Schema)
  [a :- (s/protocol s/Schema)]
  "A 1-argument function that returns values ready for use by the GraphQL API.
  Must implement clojure.lang.Fn.
  
  a must be a subtype of GraphQLValue."
  (let [a (s/constrained a resolved-graphql-value?)]
    (s/constrained
      (s/=> a
            GraphQLRuntimeOptions)
      delayed-graphql-value?)))

(s/defn MaybeDelayedGraphQLValue
  :- (s/protocol s/Schema)
  [a :- (s/protocol s/Schema)]
  "Returns a schema representing
  a must be a subtype of GraphQLValue."
  (let [a (s/constrained a resolved-graphql-value?)]
    (s/if delayed-graphql-value?
      (DelayedGraphQLValue a)
      a)))

(s/defschema AnyMaybeDelayedGraphQLValue
  (MaybeDelayedGraphQLValue GraphQLValue))

(s/defn MaybeDelayedGraphQLTypeResolver
  :- (s/protocol s/Schema)
  [a :- (s/protocol s/Schema)]
  "Returns a schema representing type resolvers
  that might return delayed GraphQL values."
  (let [a (s/constrained a resolved-graphql-value?)]
    (s/=> (MaybeDelayedGraphQLValue a)
          (s/named s/Any 'context)
          (s/named s/Any 'args)
          (s/named s/Any 'field-selection)
          (s/named s/Any 'source))))

(s/defschema AnyMaybeDelayedGraphQLTypeResolver
  (MaybeDelayedGraphQLTypeResolver GraphQLValue))

(s/defschema MaybeDelayedGraphQLFields
  {s/Keyword
   {:type AnyMaybeDelayedGraphQLValue
    (s/optional-key :args) s/Any
    (s/optional-key :resolve) AnyMaybeDelayedGraphQLTypeResolver
    (s/optional-key :description) s/Any
    (s/optional-key :default-value) s/Any}})

(s/defn resolve-with-rt-opt :- GraphQLValue
  "Resolve a MaybeDelayedGraphQLValue value, if needed, using given runtime options."
  [maybe-fn :- AnyMaybeDelayedGraphQLValue
   rt-opt :- GraphQLRuntimeOptions]
  (if (delayed-graphql-value? maybe-fn)
    (maybe-fn rt-opt)
    maybe-fn))

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

(s/defn enum :- (MaybeDelayedGraphQLValue GraphQLEnumType)
  "Creates a GraphQLEnumType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
   [enum-name :- String description values]
   (s/fn :- GraphQLEnumType
    [{{{:keys [get-or-update-type-registry]} :GraphQLService} :services
      :as _rt-opt_} :- GraphQLRuntimeOptions]
    (get-or-update-type-registry
      enum-name
      #(let [builder (-> (GraphQLEnumType/newEnum)
                         (.name enum-name)
                         (.description ^String description))
             names-and-values? (map? values)]
         (doseq [value values]
           (if names-and-values?
             (.value builder (key value) (val value))
             (if (string? value)
               (.value builder ^String value)
               ;unsure if reachable
               (.value builder ^GraphQLEnumValueDefinition value))))
         (let [graphql-enum (.build builder)]
           graphql-enum)))))

(s/defn list-type :- (MaybeDelayedGraphQLValue GraphQLList)
  [t :- (MaybeDelayedGraphQLValue GraphQLValue)]
  (s/fn :- GraphQLList
    [rt-opt :- GraphQLRuntimeOptions]
    (GraphQLList/list (-> t (resolve-with-rt-opt rt-opt)))))

(s/defn non-null :- (MaybeDelayedGraphQLValue GraphQLNonNull)
  [t :- (MaybeDelayedGraphQLValue GraphQLValue)]
  (s/fn :- GraphQLNonNull
    [rt-opt :- GraphQLRuntimeOptions]
    (GraphQLNonNull/nonNull (-> t (resolve-with-rt-opt rt-opt)))))

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

(s/defn fn->data-fetcher :- DataFetcher
  "Converts a function that takes 4 parameters (context, args, field-selection, source)
  to a GraphQL DataFetcher"
  [f :- AnyMaybeDelayedGraphQLTypeResolver
   rt-opt :- GraphQLRuntimeOptions]
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

(s/defn map-resolver :- AnyMaybeDelayedGraphQLTypeResolver
  ([k] (map-resolver k identity))
  ([k f]
   (fn [_ _ _ value]
     (when value
       (f (get value k))))))

                                        ;----- Input

(s/defn ^:private
  new-argument
  :- GraphQLArgument
  [^String arg-name
   arg-type :- AnyMaybeDelayedGraphQLValue
   ^String arg-description
   arg-default-value
   rt-opt :- GraphQLRuntimeOptions]
  (let [builder
        (-> (GraphQLArgument/newArgument)
            (.name arg-name)
            (.type (-> arg-type (resolve-with-rt-opt rt-opt)))
            (.description (or arg-description "")))]
    (when (some? arg-default-value)
      (.defaultValue builder arg-default-value))
    (.build builder)))

(s/defn ^GraphQLFieldDefinition$Builder
  add-args
  [^GraphQLFieldDefinition$Builder field
   args
   rt-opt :- GraphQLRuntimeOptions]
  (doseq [[k {arg-type :type
              arg-description :description
              arg-default-value :default
              :or {arg-description ""}}] args]
    (let [narg
          (new-argument (name k)
                        arg-type
                        arg-description
                        arg-default-value
                        rt-opt)]
      (.argument field narg)))
  field)

(s/defn new-input-field
  :- GraphQLInputObjectField
  [^String field-name
   field-type :- AnyMaybeDelayedGraphQLValue
   ^String field-description
   default-value
   rt-opt :- GraphQLRuntimeOptions]
  (let [^GraphQLInputType field-type (-> field-type
                                         (resolve-with-rt-opt rt-opt))
        _ (log/debug "New input field" field-name (pr-str field-type))
        builder
        (-> (GraphQLInputObjectField/newInputObjectField)
            (.name field-name)
            (.type field-type)
            (.description field-description))]
    (when (some? default-value)
      (.defaultValue builder default-value))
    (.build builder)))

(s/defn ^:private add-input-fields
  :- GraphQLInputObjectType$Builder
  [^GraphQLInputObjectType$Builder builder
   fields :- MaybeDelayedGraphQLFields
   rt-opt :- GraphQLRuntimeOptions]
  (doseq [[k {field-type :type
              field-description :description
              field-default-value :default-value
              :or {field-description ""}}] fields]
    (let [newf
          (new-input-field (name k)
                           field-type
                           field-description
                           field-default-value rt-opt)]
      (.field builder newf)))
  builder)

(s/defn new-input-object :- (MaybeDelayedGraphQLValue GraphQLInputObjectType)
  "Creates a GraphQLInputObjectType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
  [object-name
   description
   fields :- MaybeDelayedGraphQLFields]
  (s/fn :- GraphQLInputObjectType
    [{{{:keys [get-or-update-type-registry]} :GraphQLService} :services
      :as rt-opt} :- GraphQLRuntimeOptions]
    (get-or-update-type-registry
      object-name
      #(-> (GraphQLInputObjectType/newInputObject)
           (.name ^String object-name)
           (.description ^String description)
           (add-input-fields fields rt-opt)
           .build))))

;;----- Output

(s/defn new-field
  :- (MaybeDelayedGraphQLValue GraphQLFieldDefinition)
  [field-name
   field-type :- (MaybeDelayedGraphQLValue GraphQLOutputType)
   field-description
   field-args
   field-data-fetcher]
  (s/fn :- GraphQLFieldDefinition
    [rt-opt :- GraphQLRuntimeOptions]
    (let [^GraphQLOutputType field-type (-> field-type (resolve-with-rt-opt rt-opt))
          _ (log/debug "New field" field-name (pr-str field-type))]
      (-> (GraphQLFieldDefinition/newFieldDefinition)
          (.name ^String field-name)
          (.type field-type)
          (.description ^String field-description)
          (.dataFetcher field-data-fetcher)
          (add-args field-args rt-opt)
          .build))))

(s/defn ^:private add-fields
  :- GraphQLObjectType$Builder
  [builder :- GraphQLObjectType$Builder
   fields :- MaybeDelayedGraphQLFields
   rt-opt :- GraphQLRuntimeOptions]
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
                     (fn->data-fetcher field-resolver rt-opt))
          ^GraphQLFieldDefinition
          newf (-> newf (resolve-with-rt-opt rt-opt))]
      (.field builder newf)))
  builder)

(s/defn new-object :- (MaybeDelayedGraphQLValue GraphQLObjectType)
  "Creates a GraphQLObjectType. If a type with the same name has already been
   created, the corresponding object is retrieved from the provided or the
   default type repository."
  [object-name description interfaces fields :- MaybeDelayedGraphQLFields]
  (s/fn :- GraphQLObjectType
    [{{{:keys [get-or-update-type-registry]} :GraphQLService} :services
      :as rt-opt} :- GraphQLRuntimeOptions]
    (get-or-update-type-registry
      object-name
      #(let [builder (-> (GraphQLObjectType/newObject)
                         (.description ^String description)
                         (.name ^String object-name)
                         (add-fields fields rt-opt))]
         (doseq [^GraphQLInterfaceType interface interfaces]
           (.withInterface builder interface))
         (let [obj (.build builder)]
           obj)))))

(s/defn fn->type-resolver :- TypeResolver
  "Converts a function that takes the current object, the args
  and the global schema to a TypeResolver."
  [f :- (s/=> AnyMaybeDelayedGraphQLValue
              (s/named s/Any 'object)
              (s/named s/Any 'args)
              (s/named s/Any 'schema))
   rt-opt :- GraphQLRuntimeOptions]
  (reify TypeResolver
    (getType [_ env]
      (let [object (->clj (.getObject env))
            args (->clj (.getArguments env))
            schema (.getSchema env)]
        (-> (f object args schema)
            (resolve-with-rt-opt rt-opt))))))

(s/defn new-union :- (MaybeDelayedGraphQLValue GraphQLUnionType)
  "Creates a GraphQLUnionType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
  [^String union-name
   ^String description
   type-resolver-fn
   types]
  (s/fn :- GraphQLUnionType
    [{{{:keys [get-or-update-type-registry]} :GraphQLService} :services
      :as rt-opt} :- GraphQLRuntimeOptions]
    (get-or-update-type-registry
      union-name
      #(let [type-resolver (fn->type-resolver type-resolver-fn rt-opt)
             graphql-union (-> (GraphQLUnionType/newUnionType)
                               (.description description)
                               (.name union-name)
                               ; FIXME: this method is deprecated
                               (.typeResolver type-resolver))]
         (doseq [type types
                 :let [type (-> type (resolve-with-rt-opt rt-opt))]]
           (if (instance? GraphQLObjectType type)
             (.possibleType graphql-union ^GraphQLObjectType type)
             (.possibleType graphql-union ^GraphQLTypeReference type)))
         (.build graphql-union)))))

(defn new-ref
  [object-name]
  (GraphQLTypeReference. object-name))

(s/defn new-schema :- (MaybeDelayedGraphQLValue GraphQLSchema)
  [query :- (MaybeDelayedGraphQLValue GraphQLObjectType)]
  (s/fn :- GraphQLSchema
    [rt-opt :- GraphQLRuntimeOptions]
    (-> (GraphQLSchema/newSchema)
        (.query ^GraphQLObjectType (resolve-with-rt-opt query rt-opt))
        .build)))

(defn get-type
  "Retrieves a Type from the given schema by its name"
  [^GraphQLSchema schema
   type-name]
  (.getType schema type-name))

(s/defn new-graphql :- (MaybeDelayedGraphQLValue GraphQL)
  [schema :- (MaybeDelayedGraphQLValue GraphQLSchema)]
  (s/fn :- GraphQL
    [rt-opt :- GraphQLRuntimeOptions]
    (-> (GraphQL/newGraphQL (-> schema (resolve-with-rt-opt rt-opt)))
        .build)))

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
