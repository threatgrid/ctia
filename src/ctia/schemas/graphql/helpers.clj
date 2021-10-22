(ns ctia.schemas.graphql.helpers
  (:require [clojure
             [walk :as walk :refer [stringify-keys]]]
            [ctia.graphql.delayed :as delayed]
            [ctia.schemas.core :refer [AnyGraphQLTypeResolver
                                       AnyRealizeFnResult
                                       GraphQLRuntimeContext
                                       GraphQLValue
                                       RealizeFnResult
                                       resolve-with-rt-ctx]]
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
            GraphQLType
            GraphQLTypeReference
            GraphQLUnionType
            TypeResolver]))

(s/defschema GraphQLFields
  {s/Keyword
   {:type AnyRealizeFnResult
    (s/optional-key :args) s/Any
    (s/optional-key :resolve) AnyGraphQLTypeResolver
    (s/optional-key :description) s/Any
    (s/optional-key :default-value) s/Any}})

(s/defschema NamedTypeRegistry
  "Type registry to ensure named GraphQL named types are created exactly once.
  Contains a map with derefable types indexed by name.
  Use via get-or-update-named-type-registry."
  (s/atom {s/Str
           #_(IDeref GraphQLNamedType)
           (s/pred some?)}))

(s/defn get-or-update-named-type-registry
  ;; could return graphql.schema.GraphQLNamedType, but doesn't exist in current GraphQL version
  :- GraphQLType
  "If name exists in registry, return existing mapping. Otherwise
  atomically calls (f) and returns result after adding to registry under name."
  [type-registry :- NamedTypeRegistry
   name :- s/Str
   ;; TODO use GraphQLNamedType when available
   f :- (s/=> GraphQLType)]
  (if-some [d (@type-registry name)]
    @d ;; fast-path for readers
    ;; generate a new graphql value, or coordinate with another thread doing the same
    (let [f (bound-fn* f) ;; may be run on another thread
          {result-delay name} (swap! type-registry
                                     (fn [{existing-delay name :as oldtr}]
                                       (cond-> oldtr
                                         (not existing-delay)
                                         (assoc name (delay (f))))))]
      @result-delay)))

(s/defn create-named-type-registry
  :- NamedTypeRegistry
  []
  (atom {}))

;; TODO move to Trapperkeeper service
;; TODO: remove unused var
#_(s/def default-named-type-registry
  :- NamedTypeRegistry
  (create-named-type-registry))

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
   (when (and n (seq n))
     (re-matches #"[_A-Za-z][_0-9A-Za-z]*" n))))

(defn valid-type-names?
  [c]
  (every? valid-type-name? c))

(s/defn enum :- (RealizeFnResult GraphQLEnumType)
  "Creates a GraphQLEnumType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
  [enum-name :- String description values]
  (delayed/fn :- GraphQLEnumType
    [{{{:keys [get-or-update-named-type-registry]} :GraphQLNamedTypeRegistryService}
      :services} :- GraphQLRuntimeContext]
    (get-or-update-named-type-registry
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

(s/defn list-type :- (RealizeFnResult GraphQLList)
  [t :- (RealizeFnResult GraphQLValue)]
  (delayed/fn :- GraphQLList
    [rt-ctx :- GraphQLRuntimeContext]
    (GraphQLList/list (-> t (resolve-with-rt-ctx rt-ctx)))))

(s/defn non-null :- (RealizeFnResult GraphQLNonNull)
  [t :- (RealizeFnResult GraphQLValue)]
  (delayed/fn :- GraphQLNonNull
    [rt-ctx :- GraphQLRuntimeContext]
    (GraphQLNonNull/nonNull (-> t (resolve-with-rt-ctx rt-ctx)))))

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
        fields (mapcat (fn [[_k v]] v) selection-set)
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
  [f :- AnyGraphQLTypeResolver
   rt-ctx :- GraphQLRuntimeContext]
  (reify DataFetcher
    (get [_ env]
      (let [fragments (->clj (.getFragmentsByName env))
            context (->clj (.getContext env))
            args (->clj (.getArguments env))
            value (->clj (.getSource env))
            field-selection (env->field-selection env fragments)
            result (-> (f context args field-selection value)
                       (resolve-with-rt-ctx rt-ctx))]
        (debug "data-fetcher context:" context)
        (debug "data-fetcher args:" args)
        (debug "data-fetcher value:"  value)
        (debug "data-fetcher result:" result)
        result))))

(s/defn map-resolver :- AnyGraphQLTypeResolver
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
   arg-type :- AnyRealizeFnResult
   arg-description :- (s/maybe s/Str)
   arg-default-value
   rt-ctx :- GraphQLRuntimeContext]
  (let [builder
        (-> (GraphQLArgument/newArgument)
            (.name arg-name)
            (.type (-> arg-type (resolve-with-rt-ctx rt-ctx)))
            (.description (or ^String arg-description "")))]
    (when (some? arg-default-value)
      (.defaultValue builder arg-default-value))
    (.build builder)))

(s/defn add-args
  :- GraphQLFieldDefinition$Builder
  [^GraphQLFieldDefinition$Builder field
   args
   rt-ctx :- GraphQLRuntimeContext]
  (doseq [[k {arg-type :type
              arg-description :description
              arg-default-value :default
              :or {arg-description ""}}] args]
    (let [narg
          (new-argument (name k)
                        arg-type
                        arg-description
                        arg-default-value
                        rt-ctx)]
      (.argument field narg)))
  field)

(s/defn new-input-field
  :- GraphQLInputObjectField
  [^String field-name
   field-type :- AnyRealizeFnResult
   ^String field-description
   default-value
   rt-ctx :- GraphQLRuntimeContext]
  (let [^GraphQLInputType field-type (-> field-type
                                         (resolve-with-rt-ctx rt-ctx))
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
   fields :- GraphQLFields
   rt-ctx :- GraphQLRuntimeContext]
  (doseq [[k {field-type :type
              field-description :description
              field-default-value :default-value
              :or {field-description ""}}] fields]
    (let [newf
          (new-input-field (name k)
                           field-type
                           field-description
                           field-default-value rt-ctx)]
      (.field builder newf)))
  builder)

(s/defn new-input-object :- (RealizeFnResult GraphQLInputObjectType)
  "Creates a GraphQLInputObjectType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
  [object-name :- s/Str
   description :- s/Str
   fields :- GraphQLFields]
  (delayed/fn :- GraphQLInputObjectType
    [{{{:keys [get-or-update-named-type-registry]} :GraphQLNamedTypeRegistryService}
      :services
      :as rt-ctx} :- GraphQLRuntimeContext]
    (get-or-update-named-type-registry
      object-name
      #(-> (GraphQLInputObjectType/newInputObject)
           (.name ^String object-name)
           (.description ^String description)
           (add-input-fields fields rt-ctx)
           .build))))

;;----- Output

(s/defn new-field
  :- (RealizeFnResult GraphQLFieldDefinition)
  [field-name
   field-type :- (RealizeFnResult GraphQLOutputType)
   field-description
   field-args
   field-data-fetcher]
  (delayed/fn :- GraphQLFieldDefinition
    [rt-ctx :- GraphQLRuntimeContext]
    (let [^GraphQLOutputType field-type (-> field-type (resolve-with-rt-ctx rt-ctx))
          _ (log/debug "New field" field-name (pr-str field-type))]
      (-> (GraphQLFieldDefinition/newFieldDefinition)
          (.name ^String field-name)
          (.type field-type)
          (.description ^String field-description)
          (.dataFetcher field-data-fetcher)
          (add-args field-args rt-ctx)
          .build))))

(s/defn ^:private add-fields
  :- GraphQLObjectType$Builder
  [builder :- GraphQLObjectType$Builder
   fields :- GraphQLFields
   rt-ctx :- GraphQLRuntimeContext]
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
                     (fn->data-fetcher field-resolver rt-ctx))
          ^GraphQLFieldDefinition
          newf (-> newf (resolve-with-rt-ctx rt-ctx))]
      (.field builder newf)))
  builder)

(s/defn new-object :- (RealizeFnResult GraphQLObjectType)
  "Creates a GraphQLObjectType. If a type with the same name has already been
   created, the corresponding object is retrieved from the provided or the
   default type repository."
  [object-name :- s/Str
   description :- s/Str
   interfaces
   fields :- GraphQLFields]
  (delayed/fn :- GraphQLObjectType
    [{{{:keys [get-or-update-named-type-registry]} :GraphQLNamedTypeRegistryService} :services
      :as rt-ctx} :- GraphQLRuntimeContext]
    (get-or-update-named-type-registry
      object-name
      #(let [builder (-> (GraphQLObjectType/newObject)
                         (.description ^String description)
                         (.name ^String object-name)
                         (add-fields fields rt-ctx))]
         (doseq [^GraphQLInterfaceType interface interfaces]
           (.withInterface builder interface))
         (let [obj (.build builder)]
           obj)))))

(s/defn fn->type-resolver :- TypeResolver
  "Converts a function that takes the current object, the args
  and the global schema to a TypeResolver."
  [f :- (s/=> AnyRealizeFnResult
              (s/named s/Any 'object)
              (s/named s/Any 'args)
              (s/named s/Any 'schema))
   rt-ctx :- GraphQLRuntimeContext]
  (reify TypeResolver
    (getType [_ env]
      (let [object (->clj (.getObject env))
            args (->clj (.getArguments env))
            schema (.getSchema env)]
        (-> (f object args schema)
            (resolve-with-rt-ctx rt-ctx))))))

(s/defn new-union :- (RealizeFnResult GraphQLUnionType)
  "Creates a GraphQLUnionType. If a type with the same name has already been
   created, the corresponding object is retrieved instead."
  [union-name :- s/Str
   description :- s/Str
   type-resolver-fn
   types]
  (delayed/fn :- GraphQLUnionType
    [{{{:keys [get-or-update-named-type-registry]} :GraphQLNamedTypeRegistryService} :services
      :as rt-ctx} :- GraphQLRuntimeContext]
    (get-or-update-named-type-registry
      union-name
      #(let [type-resolver (fn->type-resolver type-resolver-fn rt-ctx)
             graphql-union (-> (GraphQLUnionType/newUnionType)
                               (.description description)
                               (.name union-name)
                               ; FIXME: this method is deprecated
                               (.typeResolver type-resolver))]
         (doseq [type types
                 :let [type (-> type (resolve-with-rt-ctx rt-ctx))]]
           (if (instance? GraphQLObjectType type)
             (.possibleType graphql-union ^GraphQLObjectType type)
             (.possibleType graphql-union ^GraphQLTypeReference type)))
         (.build graphql-union)))))

(defn new-ref
  [object-name]
  (GraphQLTypeReference. object-name))

(s/defn new-schema :- (RealizeFnResult GraphQLSchema)
  [query :- (RealizeFnResult GraphQLObjectType)]
  (delayed/fn :- GraphQLSchema
    [rt-ctx :- GraphQLRuntimeContext]
    (-> (GraphQLSchema/newSchema)
        (.query ^GraphQLObjectType (resolve-with-rt-ctx query rt-ctx))
        .build)))

(defn get-type
  "Retrieves a Type from the given schema by its name"
  [^GraphQLSchema schema
   type-name]
  (.getType schema type-name))

(s/defn new-graphql :- (RealizeFnResult GraphQL)
  [schema :- (RealizeFnResult GraphQLSchema)]
  (delayed/fn :- GraphQL
    [rt-ctx :- GraphQLRuntimeContext]
    (-> (GraphQL/newGraphQL (-> schema (resolve-with-rt-ctx rt-ctx)))
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
