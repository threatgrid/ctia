(ns ctia.schemas.core
  (:require
   [ctia.flows.hooks-service.schemas :as hooks-schemas]
   [ctia.graphql.delayed :as delayed]
   [ctia.schemas.services :as external-svc-fns]
   [ctia.schemas.utils :as csu]
   [ctia.store-service.schemas :refer [GetStoreFn]]
   [ctim.domain.id :as id]
   [ctim.schemas.bundle :as bundle]
   [ctim.schemas.common :as cos]
   [ctim.schemas.verdict :as vs]
   [ctim.schemas.vocabularies :as vocs]
   [flanders.schema :as f-schema]
   [flanders.spec :as f-spec]
   [schema-tools.core :as st]
   [schema.core :as s :refer [Bool Str]]))

(s/defschema Port
  "A port number"
  (s/constrained s/Int pos?))

(s/defschema APIHandlerServices
  "Maps of services available to routes"
  {:ConfigService                   (-> external-svc-fns/ConfigServiceFns
                                        (csu/select-all-keys
                                          #{:get-config
                                            :get-in-config}))
   :CTIAHTTPServerService           {:get-port    (s/=> Port)
                                     :get-graphql (s/=> graphql.GraphQL)}
   :HooksService                    (-> hooks-schemas/ServiceFns
                                        (csu/select-all-keys
                                          #{:apply-event-hooks
                                            :apply-hooks}))
   :StoreService                    {:get-store GetStoreFn}
   :IAuth                           {:identity-for-token (s/=> s/Any s/Any)}
   :GraphQLNamedTypeRegistryService {:get-or-update-named-type-registry
                                     (s/=> graphql.schema.GraphQLType
                                           s/Str
                                           (s/=> graphql.schema.GraphQLType))}
   :IEncryption                     {:encrypt (s/=> s/Any s/Any)
                                     :decrypt (s/=> s/Any s/Any)}
   :FeaturesService                 {:enabled?      (s/=> s/Bool s/Keyword)
                                     :feature-flags (s/=> [s/Str])}})

(s/defschema HTTPShowServices
  (-> APIHandlerServices
      (csu/select-service-subschema
        {:ConfigService #{:get-in-config}
         :CTIAHTTPServerService #{:get-port}})
      csu/open-service-schema))

(s/defschema DelayedRoutes
  "Function taking a map of services and returning routes
  (eg., return value of `entity-crud-routes`)"
  (s/=> s/Any APIHandlerServices))

(def base-stored-entity-entries
  {:id s/Str
   :owner s/Str
   :groups [s/Str]
   :created java.util.Date
   (s/optional-key :modified) java.util.Date})

(s/defschema RealizeFnServices
  "Maps of service functions available for realize-fns"
  (-> APIHandlerServices
      (csu/select-service-subschema
        {:ConfigService #{:get-in-config}
         :CTIAHTTPServerService #{:get-port}
         :StoreService #{:get-store}
         :GraphQLNamedTypeRegistryService #{:get-or-update-named-type-registry}
         :IEncryption #{:decrypt
                        :encrypt}})))

(s/defn APIHandlerServices->RealizeFnServices
  :- RealizeFnServices
  [services :- APIHandlerServices]
  (csu/select-service-subgraph
    services
    RealizeFnServices))

(s/defschema GraphQLRuntimeContext
  "A context map to resolve a DelayedGraphQLValue"
  {:services RealizeFnServices})

(s/defn DelayedGraphQLValue
  :- (s/protocol s/Schema)
  [a :- (s/protocol s/Schema)]
  "An opaque wrapper for a 1-argument function that takes a
  [[GraphQLRuntimeContext]] and returns a value
  conforming to `a` (which itself must conform to [[delayed/resolved-graphql-value?]]).
  
  Use [[resolve-with-rt-ctx]] to call the opaque function.
  
  Note: does not check `a`, it is currently for documentation only."
  (s/pred delayed/delayed-graphql-value?))

(s/defn RealizeFnResult
  :- (s/protocol s/Schema)
  "The return value of a realize-fn either is an opaque function
  that expects a map of service maps, otherwise it is considered
  'resolved'."
  [a :- (s/protocol s/Schema)]
  (let [a (s/constrained a delayed/resolved-graphql-value?)]
    (s/if delayed/delayed-graphql-value?
      (DelayedGraphQLValue a)
      a)))

(s/defn RealizeFnReturning
  :- (s/protocol s/Schema)
  [return :- (s/protocol s/Schema)]
  (s/=>* return
         [(s/named s/Any 'new-object)
          (s/named s/Any 'id)
          (s/named s/Any 'tempids)
          (s/named s/Any 'owner)
          (s/named s/Any 'groups)]
         [(s/named s/Any 'new-object)
          (s/named s/Any 'id)
          (s/named s/Any 'tempids)
          (s/named s/Any 'owner)
          (s/named s/Any 'groups)
          (s/named s/Any 'prev-object)]))

(s/defschema GraphQLValue
  (s/pred
    delayed/resolved-graphql-value?))

(s/defschema ResolvedRealizeFn
  (RealizeFnReturning GraphQLValue))

(s/defschema AnyRealizeFnResult
  (RealizeFnResult GraphQLValue))

(s/defschema RealizeFn
  (RealizeFnReturning AnyRealizeFnResult))

(s/defn resolve-with-rt-ctx
  "Resolve a RealizeFnResult value, if needed, using given runtime options."
  [graphql-val :- s/Any
   rt-ctx :- GraphQLRuntimeContext]
  {:post [(delayed/resolved-graphql-value? %)]}
  (if (delayed/delayed-graphql-value? graphql-val)
    ((delayed/unwrap graphql-val)
     rt-ctx)
    graphql-val))

(s/defn GraphQLTypeResolver
  :- (s/protocol s/Schema)
  [a :- (s/protocol s/Schema)]
  "Returns a schema representing type resolvers
  that might return delayed GraphQL values."
  (let [a (s/constrained a delayed/resolved-graphql-value?)]
    (s/=> (RealizeFnResult a)
          (s/named s/Any 'context)
          (s/named s/Any 'args)
          (s/named s/Any 'field-selection)
          (s/named s/Any 'source))))

(s/defschema AnyGraphQLTypeResolver
  (GraphQLTypeResolver GraphQLValue))

(s/defn lift-realize-fn-with-context
  :- ResolvedRealizeFn
  [realize-fn :- RealizeFn
   rt-ctx :- GraphQLRuntimeContext]
  (fn [& args]
    (-> realize-fn
        (apply args)
        (resolve-with-rt-ctx
          rt-ctx))))

(s/defschema Field
  (s/conditional
    keyword? s/Keyword
    :else s/Str))

(s/defschema Entity
  (st/merge
   {:entity                s/Keyword
    :plural                s/Keyword
    :new-spec              (s/either s/Keyword s/Any)
    :schema                (s/protocol s/Schema)
    :partial-schema        (s/protocol s/Schema)
    :partial-list-schema   (s/protocol s/Schema)
    :stored-schema         (s/protocol s/Schema)
    :partial-stored-schema (s/protocol s/Schema)
    :es-store              s/Any
    :es-mapping            {s/Any s/Any}}
   (st/optional-keys
    {:fields            [Field]
     :sort-fields       [Field]
     :new-schema        (s/protocol s/Schema)
     :route-context     s/Str
     :services->routes  DelayedRoutes
     :tags              [s/Str]
     :capabilities      #{s/Keyword}
     :no-bulk?          s/Bool
     :no-api?           s/Bool
     :realize-fn        RealizeFn
     :searchable-fields (s/protocol s/Schema)})))

(s/defschema OpenCTIMSchemaVersion
  {(s/optional-key :schema_version) s/Str})

(s/defschema CTIAEntity
  (st/merge
   OpenCTIMSchemaVersion
   (st/optional-keys
    {:authorized_users [Str]
     :authorized_groups [Str]
     :owner s/Str
     :groups [s/Str]})))

(s/defschema CTIAStoredEntity
  (st/merge CTIAEntity
            base-stored-entity-entries))

(defmacro defschema [name-sym ddl spec-kw-ns]
  `(do
     (s/defschema ~name-sym
       (f-schema/->schema ~ddl))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-stored-schema
  [name-sym sch]
  `(do
     (s/defschema ~name-sym
       (csu/recursive-open-schema-version
        (st/merge
         ~sch
         CTIAStoredEntity)))))

(defmacro def-advanced-acl-schema [{:keys [name-sym
                                           ddl
                                           spec-kw-ns
                                           open?]}]
  `(do
     (s/defschema ~name-sym
       (cond-> (csu/recursive-open-schema-version
                (st/merge
                 (f-schema/->schema ~ddl)
                 CTIAEntity))
         ~open? st/open-schema))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(def-advanced-acl-schema {:name-sym ~name-sym
                             :ddl ~ddl
                             :spec-kw-ns ~spec-kw-ns}))

;; verdict

(def-acl-schema Verdict
  vs/Verdict
  "verdict")

;; Bundle

(def-acl-schema CTIMNewBundle
  bundle/NewBundle
  "new-bundle")

(def-acl-schema CTIMBundle
  bundle/Bundle
  "bundle")


;; Casebooks should be considered fully separate from CTIM
(s/defschema NewBundle
  (st/dissoc
   (csu/recursive-open-schema-version CTIMNewBundle) :casebooks))

(s/defschema Bundle
  (st/dissoc
   (csu/recursive-open-schema-version CTIMBundle) :casebooks))

;; common

(s/defschema TLP
  (f-schema/->schema
   cos/TLP))

(defschema Observable
  cos/Observable
  "common.observable")

(defschema Reference
  cos/Reference
  "common.reference")

(defschema ID
  cos/ID
  "common.id")

(def TransientID s/Str)

(s/defschema TempIDs
  "Mapping table between transient and permanent IDs"
  {TransientID ID})

(s/defschema StatusInfo
  "Status information for a specific instance of CTIA"
  {:status (s/enum :ok :error :unknow)})

(s/defschema VersionInfo
  "Version information for a specific instance of CTIA"
  {:base Str
   :ctim-version Str
   :ctia-build Str
   :beta Bool
   :ctia-config Str
   :ctia-supported_features [Str]})

;; vocabularies

(defschema ObservableTypeIdentifier
  vocs/ObservableTypeIdentifier
  "vocab.observable-type-id")

(s/defschema GetEntitiesServices
  {:FeaturesService {:enabled? (s/=> s/Bool s/Keyword)
                     s/Keyword s/Any}
   s/Keyword        s/Any})

(defn transient-id?
  [id]
  (and id (some? (re-matches id/transient-id-re id))))
