(ns ctia.schemas.graphql
  (:require [clojure.tools.logging :as log])
  (:import graphql.GraphQL)
  (:import (graphql.schema GraphQLArgument
                           GraphQLInterfaceType
                           GraphQLObjectType
                           GraphQLEnumType
                           GraphQLUnionType
                           GraphQLList
                           GraphQLFieldDefinition
                           GraphQLSchema
                           GraphQLTypeReference
                           DataFetcher
                           TypeResolver
                           
                           GraphQLNonNull))
  (:import (graphql.Scalars))
  (:require [clj-time.coerce :as time-coerce])
  (:import [org.joda.time.format DateTimeFormat ISODateTimeFormat])
  (:require [ctia.store :refer :all]
            [schema.core :as s]
            ;;[clj-momo.lib.time :refer [date->iso8601]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :refer [factory:short-id->long-id]]))


;; this will be in clj-momo.lib.time
(def ^:private iso8601-utc-formatter (ISODateTimeFormat/dateTimeNoMillis))

(defn date->iso8601
  "Given a Java Dat Object, return a string containing the is08601
  representation, using UTC timezone."
  [date]
  (.print iso8601-utc-formatter (time-coerce/from-date date)))


;; TODOS
;; - Learn to use Enum values and replace current String types with them
;; - Complete Indicator schema
;;   - add the rest of the fields
;;   - indicator(id) - defined but no tests
;;   - indicators search - defined but no tests
;; - judgements search arguments
;; - Complete Sighting schema
;;   - add the rest of the fields
;;   - sighting(id)  - defined but no tests
;;   - sightings search - defined but no tests
;;   - Add sighting lookup to Observable type
;; - Feedback support
;;   - Feedback object schema
;;   - as part of base entity, should be a feedback field which is a Connection to Feedback objects


(defn gql-field
  "A function for creating GraphQLFieldDefinitions builders, it
  required the values that define a minimally functional field
  definition. It takes the following arguments in order:
  
  * fname - the field name, as a string
  * type - A GraphQLType, or a  GraphQLTypeReference
  * description - A string description of the field, will be used in the schema documentation
  * fetcher - an object implmenting the DataFetcher interface" 
  [fname ftype description fetcher]
  (.. (GraphQLFieldDefinition/newFieldDefinition)
      (name fname)
      (type ftype)
      (description description)
      (dataFetcher  fetcher)))


(defn list-type
  "Returns a GraphQLList Type of the specified subtype"
  [element-type]
  (new GraphQLList element-type))

;; Some commonly used scalar types
(def non-null-string (new GraphQLNonNull graphql.Scalars/GraphQLString))
(def non-null-int (new GraphQLNonNull graphql.Scalars/GraphQLInt))


(defn graphql-vocab [name description value-description-map]
  (let [typedef (-> (GraphQLEnumType/newEnum)
                    (.name name)
                    (.description description))]
    (doseq [k (keys value-description-map)]
      (.value typedef k (get value-description-map k) k))
    typedef))

(defn graphql-openvocab [name description value-description-map])


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


(defn map-fetcher
  "Returns a DataFetcher which reads a key from the source object
using get, and applies a transform if supplied."
  ([key]
   (map-fetcher key identity))
  ([key transform]
   (reify DataFetcher
     (get [_ env]
       (let [obj (.getSource env)]
         (log/debug "map-fetcher called on: " (pr-str obj) " key: " (pr-str key))
         (let [val (transform (get obj key))]
           (log/debug "map-fetcher returning: " val)
           val))))))

(defn unimplemented-fetcher
  "Placeholder for unimplemented field fetchers."
  [msg]
  (reify DataFetcher
    (get [_ env]
      (throw (Exception. (str "Unimplemented Fetcher: " msg))))))

(defn static-fetcher
  "Returns a DataFetcher which always returns the same constant value"
  [value]
  (reify DataFetcher
    (get [_ env]
      (log/debug "static-fetcher called: " value)
      value)))


(defn entity-for-id [id]
  (let [[orig docid] (re-matches #".*?([^/]+)\z" id) ]
    (let [[_ entity] (re-matches #"([^-]+)-.*\z" docid)]
      entity)))

(defn read-fn-for-entity [entity]
  (ns-resolve (find-ns 'ctia.store) (symbol (str "read-" entity))))

(defn entity-fetch
  ([id entity read-fn]
   (log/info "entity-fetch called, id is: " (pr-str id))
   (log/info "entity-fetch entity is: " (pr-str entity))
   (log/info "entity-fetch read-fn is: " read-fn)
   (let [result (read-store (keyword entity) read-fn id)]
     (log/debug"entity-fetch read-store result: " (pr-str result))
     (update result
             :id (factory:short-id->long-id (keyword entity) get-http-show))))
  ([id entity]
   (entity-fetch id entity (read-fn-for-entity entity)))
  ([id]
   (entity-fetch id (entity-for-id id))))

(defn entity-by-id-fetcher
  "Returns a DataFetcher which will look up an entity using the `id` argument to the field selector,
  using the ctia.store/read-store method and the provided reader-fn."
  ([]
   (entity-by-id-fetcher nil nil))
  ([entity read-fn]
   (reify DataFetcher
     (get [_ env]
       (let [id (.getArgument env "id")
             entity (or entity (entity-for-id id))
             read-fn (or read-fn (read-fn-for-entity entity))]
         (entity-fetch id entity read-fn))))))


(defn entity-by-key-fetcher
  "Returns a DataFetcher which will look up an entity using the `id` argument to the field selector,
  using the ctia.store/read-store method and the provided reader-fn."
  ([key]
   (reify DataFetcher
     (get [_ env]
       (let [id (get (.getSource env) key)
             entity (entity-for-id id)
             read-fn (read-fn-for-entity entity)]
         (entity-fetch id entity read-fn))))))

(defn with-long-ids [entity l]
  (let [f (factory:short-id->long-id entity get-http-show)
        t (fn [o]
            (update o :id f))]
    (map t l)))

(defn with-long-id [entity obj]
  (let [f (factory:short-id->long-id entity get-http-show)
        t (fn [o]
            (update o :id f))]
    (when obj (t obj))))


(defn add-base-entity-fields
  "Given an object builders, adds all of the BaseEntity fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (gql-field "id"
                         non-null-string
                         "ID of the object, a full URI"
                         (map-fetcher :id)))
      (.field (gql-field "type"
                         non-null-string
                         "the CTIM entity type of the object"
                         (map-fetcher :type)))
      (.field (gql-field "schema_version"
                         non-null-string
                         "the CTIM schema_version of the object"
                         (map-fetcher :schema_version)))
      (.field (gql-field "revision"
                         graphql.Scalars/GraphQLInt
                         "the revision of the object"
                         (map-fetcher :revision)))
      (.field (gql-field "external_ids"
                         (list-type (graphql.Scalars/GraphQLString))
                         "a list of external IDs other systems use to refer to this object"
                         (map-fetcher :external_ids)))
      (.field (gql-field "timestamp"
                         graphql.Scalars/GraphQLString
                         "an ISO8601 timestamp for when the object was defined or modified"
                         (map-fetcher :timestamp date->iso8601)))
      (.field (gql-field "language"
                         graphql.Scalars/GraphQLString
                         "optional language identifier for the human language of the object"
                         (map-fetcher :language)))
      (.field (gql-field "tlp"
                         graphql.Scalars/GraphQLString
                         "the Traffic Light Protocol value for the object, describing the policy for sharing it."
                         (map-fetcher :tlp)))))

(defn add-describable-entity-fields
  "Given an object builders, adds all of the DescribableEntity fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (gql-field "title"
                         graphql.Scalars/GraphQLString
                         "a short Title for the object, the primary human idenifier for the object.  Does not need to be unique."
                         (map-fetcher :title)))
      (.field (gql-field "short_description"
                         graphql.Scalars/GraphQLString
                         "a single line, short description of the object"
                         (map-fetcher :short_description)))
      (.field (gql-field "description"
                         graphql.Scalars/GraphQLString
                         "the full fescription of the object, Markdown text supported."
                         (map-fetcher :description)))))

(defn add-sourced-object-fields
  "Given an object builders, adds all of the SourcedObject fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (gql-field "source"
                         graphql.Scalars/GraphQLString
                         "The source of the object"
                         (map-fetcher :source)))
      (.field (gql-field "source_uri"
                         graphql.Scalars/GraphQLString
                         "a URI for more information about the object, from the source"
                         (map-fetcher :source_uri)))))

(defn- remove-nil-values [record]
  (apply dissoc                                                                                            
         record                                                                                                  
         (for [[k v] record :when (nil? v)] k)))



(defn add-connect-type-arguments
  "Add the required edge field arguments for Relay support."
  [field-builder]
  (-> field-builder
      (.argument (.. (GraphQLArgument/newArgument)
                     (name "first")
                     (type graphql.Scalars/GraphQLInt)
                     (defaultValue 50)
                     (description "return the first N objects")))
      (.argument (.. (GraphQLArgument/newArgument)
                     (name "after")
                     (type graphql.Scalars/GraphQLString)
                     (description "start from this object")
                     (type graphql.Scalars/GraphQLString)))
      (.argument (.. (GraphQLArgument/newArgument)
                     (name "last")
                     (type graphql.Scalars/GraphQLInt)
                     (description "return the last N objects")))
      (.argument (.. (GraphQLArgument/newArgument)
                     (name "before")
                     (type graphql.Scalars/GraphQLString)
                     (description "start from this object")))))

(declare resolve-type)
(def entity-type-resolver  (reify TypeResolver
                             (getType [_ obj]
                               (resolve-type obj))))

(def NodeInterface
  (-> (GraphQLInterfaceType/newInterface)
      (.name "Node")
      (.description "A Node interface, for Relay support")
      (.typeResolver entity-type-resolver)
      (.field (gql-field "id"
                         graphql.Scalars/GraphQLID
                         "ID of the object"
                         (map-fetcher :id)))      
      (.build)))

(def BaseEntityInterface
  (-> (GraphQLInterfaceType/newInterface)
      (.name "BaseEntity")
      (.description "A top level entity in our data model")
      (.typeResolver entity-type-resolver)
      (add-base-entity-fields)
      (.build)))


(def DescribableEntityInterface
  (-> (GraphQLInterfaceType/newInterface)
      (.name "DescribablyEntity")
      (.description "A decribable, top-level entity in our data model")
      (.typeResolver entity-type-resolver)
      (add-describable-entity-fields)
      (.build)))

(def SourcedObjectInterface
  (-> (GraphQLInterfaceType/newInterface)
      (.name "SourcedObject")
      (.description "A source object in our data model")
      (.typeResolver entity-type-resolver)
      (add-sourced-object-fields)
      (.build)))




(def PageInfoType
  (-> (GraphQLObjectType/newObject)
      (.name "PageInfo")
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "totalHits")
                  (.type graphql.Scalars/GraphQLInt)
                  (.dataFetcher (map-fetcher :total-hits))))
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "hasPreviousPage")
                  (.type graphql.Scalars/GraphQLBoolean)
                  (.dataFetcher (map-fetcher :has-previous-page))))
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "hasNextPage")
                  (.type graphql.Scalars/GraphQLBoolean)
                  (.dataFetcher (map-fetcher :has-next-page))))
      (.build)))


(def RelationshipType
  (-> (GraphQLObjectType/newObject)
      (.name "Relationship")
      (.withInterface NodeInterface)
      (.withInterface BaseEntityInterface)
      (.withInterface SourcedObjectInterface)
      (.withInterface DescribableEntityInterface)
      (add-base-entity-fields)
      (add-sourced-object-fields)
      (add-describable-entity-fields)
      (.field (gql-field "source_ref"
                         non-null-string
                         "The ID of the source object"
                         (map-fetcher :source_ref)))
      (.field (gql-field "target_ref"
                         non-null-string
                         "The ID of the target object"
                         (map-fetcher :target_ref)))
      (.field (gql-field "relationship_type"
                         non-null-string
                         "The type of the relationship, see /ctia/doc/defined_relationships.md"
                         (map-fetcher :relationship_type)))
      
      ;; GET TARGET WORKING
      (.field (gql-field "target"
                         NodeInterface
                         "The target object of this relationship"
                         (entity-by-key-fetcher :target_ref)))
      
      ;; GET SOURCE WORKING
      
      
      (.build)))

(def RelationshipEdgeType
  (-> (GraphQLObjectType/newObject)
      (.name "RelationshipEdge")
      (.field (.. (GraphQLFieldDefinition/newFieldDefinition)
                  (name "cursor")
                  (type non-null-string)
                  (dataFetcher (map-fetcher :cursor))))
      (.field (.. (GraphQLFieldDefinition/newFieldDefinition)
                  (name "node")
                  (type RelationshipType)
                  (dataFetcher (map-fetcher :node))))
      (.build)))

(def RelationshipConnectionType
  (-> (GraphQLObjectType/newObject)
      (.name "RelationshipConnection")
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "pageInfo")
                  (.type PageInfoType)
                  (.dataFetcher (map-fetcher :page-info))))
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "edges")
                  (.type (new GraphQLList RelationshipEdgeType))
                  (.dataFetcher (map-fetcher :edges))))
      (.build)))


(defn entity-search-fetcher
  "Returns a DataFetcher which does a query string search for the specified entities."
  ([entity]
   (entity-search-fetcher entity (constantly {})))
  ([entity default-filter-fn]
   (entity-search-fetcher entity default-filter-fn (constantly "*")))
  ([entity default-filter-fn default-query-string-fn]
   (reify DataFetcher
     (get [_ env]
       (let [arguments (->clj (.getArguments env))
             qstring (or (:query arguments) (default-query-string-fn env))
             filters (remove-nil-values
                      (merge (dissoc arguments :query :sort_by :sort_order :first :after :last)
                             (default-filter-fn env)))
             fields (->clj (.getFields env))
             cursor (or (get arguments :after)
                        (get arguments :before)
                        "0")
             offset (try (. Integer parseInt cursor)
                         (catch Exception e 0)
                         (finally 0))
             limit (:first arguments)
             query-params {:limit limit
                           :offset offset}]
         (log/info "entity-search-fetcher called with entity: " entity)
         (log/info "entity-search-fetcher called with query: " (pr-str qstring))
         (log/info "entity-search-fetcher called with filters: " (pr-str filters))
         (log/info "entity-search-fetcher calls with query params: " (pr-str query-params))
         (let [results (query-string-search-store
                        entity
                        query-string-search
                        qstring
                        filters
                        query-params)
               total (get (:paging results) :total-hits 0)]
           (log/info "entity-search-fetcher results: " (pr-str results))
           {:page-info {:total-hits (get (:paging results) :total-hits)
                        :has-next-page (< (+ limit offset) total)
                        :has-previous-page (and (:last arguments) (not (= offset 0)))}
            :edges (map-indexed
                    (fn [i n]
                      {:node n
                       :cursor (+ i offset)})
                    (with-long-ids 
                      :entity (:data results)))}))))))

(defn add-relatable-entity-fields
  "Given an object builders, adds all of the RelatableObject fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "relationships")
                  (.description "A connection containing the Relationship objects that has this object a their source.")
                  (.type RelationshipConnectionType)
                  add-connect-type-arguments
                  ;; our Relationship specific arguments
                  (.argument (.. (GraphQLArgument/newArgument)
                                 (name "query")
                                 (type graphql.Scalars/GraphQLString)
                                 (description "a Lucense query string, will only return Relationships matching it.")))
                  (.argument (.. (GraphQLArgument/newArgument)
                                 (name "relationship_type")
                                 (type graphql.Scalars/GraphQLString)
                                 (description "restrict to Relations with the specified relationship_type.")))
                  (.argument (.. (GraphQLArgument/newArgument)
                                 (name "target_type")
                                 (type graphql.Scalars/GraphQLString)
                                 (description "restrict to Relationships whose target is of the specified CTIM entity type.")))
                  (.dataFetcher
                   (entity-search-fetcher :relationship
                                          (fn [env] {:source_ref (:id (.getSource env))})))))))


(def RelatableEntityInterface
  (-> (GraphQLInterfaceType/newInterface)
      (.name "RelatableEntity")
      (.description "An entity which can be a source or target of a Relationship")
      (.typeResolver entity-type-resolver)
      (add-relatable-entity-fields)
      (.build)))


(def ValidTimeType
  (-> (GraphQLObjectType/newObject)
      (.name "ValidTime")
      (.field (gql-field "start_time"
                         graphql.Scalars/GraphQLString
                         "The start of the time range this entity is valid for, an ISO8601 timestamp"
                         (map-fetcher :start_time date->iso8601)))
      (.field (gql-field "end_time"
                         graphql.Scalars/GraphQLString
                         "The end of the time range this entity is valid for, an ISO8601 timestamp.  If not present, it's good forever."
                         (map-fetcher :end_time date->iso8601)))
      (.build)))



(def JudgementType
  (-> (GraphQLObjectType/newObject)
      (.name "Judgement")
      (.withInterface NodeInterface)
      (.withInterface BaseEntityInterface)
      (.withInterface SourcedObjectInterface)
      (.withInterface RelatableEntityInterface)
      (add-base-entity-fields)
      (add-sourced-object-fields)
      (add-relatable-entity-fields)
      (.field (gql-field "observable"
                         (new GraphQLTypeReference "Observable")
                         "The Observable this judgement pertains too"
                         (map-fetcher :observable)))
      (.field (gql-field "disposition"
                         graphql.Scalars/GraphQLInt
                         "The disposition numeric identifier for the disposition of the observable"
                         (map-fetcher :disposition)))
      (.field (gql-field "disposition_name"
                         graphql.Scalars/GraphQLString
                         "The human-readable string identifier for the disposition of the observable"
                         (map-fetcher :disposition_name)))
      (.field (gql-field "priority"
                         graphql.Scalars/GraphQLInt
                         "The disposition numeric identifier for the disposition of the observable"
                         (map-fetcher :priority int)))
      (.field (gql-field "confidence"
                         graphql.Scalars/GraphQLString
                         "The confidence with which the judgement was made."
                         (map-fetcher :confidence)))
      (.field (gql-field "severity"
                         graphql.Scalars/GraphQLString
                         "The severity of the threat"
                         (map-fetcher :severity)))
      (.field (gql-field "valid_time"
                         ValidTimeType
                         "The time range for which this Judgement is considered valid."
                         (map-fetcher :valid_time)))
      (.field (gql-field "reason"
                         graphql.Scalars/GraphQLString
                         "The reason the judgement was made."
                         (map-fetcher :reason)))
      (.field (gql-field "reason_uri"
                         graphql.Scalars/GraphQLString
                         "A URI where details on the reason are available."
                         (map-fetcher :reason_uri)))
      (.build)))

(def IndicatorType
  (-> (GraphQLObjectType/newObject)
      (.name "Indicator")
      (.withInterface NodeInterface)
      (.withInterface BaseEntityInterface)
      (.withInterface SourcedObjectInterface)
      (.withInterface DescribableEntityInterface)
      (.withInterface RelatableEntityInterface)
      (add-base-entity-fields)
      (add-sourced-object-fields)
      (add-describable-entity-fields)
      (add-relatable-entity-fields)
      (.field (gql-field "valid_time"
                         ValidTimeType
                         "The time range for which this Judgement is considered valid."
                         (map-fetcher :valid_time)))
      (.field (gql-field "negate"
                         graphql.Scalars/GraphQLBoolean
                         "when true, the absence of this pattern is the indicator"
                         (map-fetcher :negate)))
      
      (.field (gql-field "indicator_type"
                         graphql.Scalars/GraphQLString                         
                         "The time range for which this Judgement is considered valid."
                         (map-fetcher :indicator_type)))

      (.field (gql-field "tags"
                         (list-type graphql.Scalars/GraphQLString)                         
                         "tags associated with this indicator."
                         (map-fetcher :tags)))
      
      (.build)))


(comment
        (f/optional-entries
         
         (f/entry :composite_indicator_expression CompositeIndicatorExpression)
         (f/entry :likely_impact f/any-str
                  :description (str "likely potential impact within the relevant "
                                    "context if this Indicator were to occur"))
         (f/entry :confidence v/HighMedLow
                  :description (str "level of confidence held in the accuracy of this "
                                    "Indicator"))
         (f/entry :kill_chain_phases f/any-str-seq
                  :comment "simplified"
                  :description "relevant kill chain phases indicated by this Indicator")
         (f/entry :test_mechanisms f/any-str-seq
                  :comment "simplified"
                  :description (str "Test Mechanisms effective at identifying the "
                                    "cyber Observables specified in this cyber threat "
                                    "Indicator"))
         (f/entry :specification (f/conditional
                                  #(= "Judgement"   (:type %)) JudgementSpecification
                                  #(= "ThreatBrain" (:type %)) ThreatBrainSpecification
                                  #(= "Snort"       (:type %)) SnortSpecification
                                  #(= "SIOC"        (:type %)) SIOCSpecification
                                  #(= "OpenIOC"     (:type %)) OpenIOCSpecification))))

(def SightingType
  (-> (GraphQLObjectType/newObject)
      (.name "Sighting")
      (.withInterface NodeInterface)
      (.withInterface BaseEntityInterface)
      (.withInterface SourcedObjectInterface)
      (.withInterface DescribableEntityInterface)
      (.withInterface RelatableEntityInterface)
      (add-base-entity-fields)
      (add-sourced-object-fields)
      (add-describable-entity-fields)
      (add-relatable-entity-fields)
      ;; add the fields here
      (.build)))


;; ID needs to be base64 encoded here, since it's opaque, verdicts only have an ID
;; for the purposes of our storage subsystem and our quick verdict lookup support
(def VerdictType
  (-> (GraphQLObjectType/newObject)
      (.name "Verdict")
      ;; should implement NodeInterface
      ;; do they even have an id????
      (.field (gql-field "id"
                         graphql.Scalars/GraphQLID
                         "An opaque ID for the verdict node, this is NOT an entity identifier."
                         (map-fetcher :id)))
      (.field (gql-field "type"
                         non-null-string
                         "type of the object"
                         (map-fetcher :type)))
      (.field (gql-field "schema_version"
                         non-null-string
                         "schema_version of the object"
                         (map-fetcher :type)))
      (.field (gql-field "disposition_name"
                         graphql.Scalars/GraphQLString
                         "The human-readable string identifier for the disposition of the observable"
                         (map-fetcher :disposition_name)))
      (.field (gql-field "disposition"
                         graphql.Scalars/GraphQLInt
                         "The disposition numeric identifier for the disposition of the observable"
                         (map-fetcher :disposition)))
      (.field (gql-field "judgement"
                         JudgementType
                         "The confidence with which the judgement was made."
                         (reify DataFetcher
                           (get [_ env]
                             (with-long-id :judgement
                               (read-store
                                :judgement read-judgement
                                (:judgement_id (.getSource env))
                                ))))))
      (.build)))



(def JudgementEdgeType
  (-> (GraphQLObjectType/newObject)
      (.name "JudgementEdge")
      (.field (.. (GraphQLFieldDefinition/newFieldDefinition)
                  (name "cursor")
                  (type non-null-string)
                  (dataFetcher (map-fetcher :cursor))))
      (.field (.. (GraphQLFieldDefinition/newFieldDefinition)
                  (name "node")
                  (type JudgementType)
                  (dataFetcher (map-fetcher :node))))
      (.build)))

(def JudgementConnectionType
  (-> (GraphQLObjectType/newObject)
      (.name "JudgementConnection")
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "pageInfo")
                  (.type PageInfoType)
                  (.dataFetcher (map-fetcher :page-info))))
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "edges")
                  (.type (new GraphQLList JudgementEdgeType))
                  (.dataFetcher (map-fetcher :edges))))
      (.build)))


(def ObservableType
  (-> (GraphQLObjectType/newObject)
      (.name "Observable")
      (.field (gql-field "type"
                         non-null-string
                         "the type of the observable"
                         (map-fetcher :type)))
      (.field (gql-field "value"
                         non-null-string
                         "The value of the observable, a string representation."
                         (map-fetcher :value)))

      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "verdict")
                  (.type VerdictType)
                  (.description "The Verdict object for the observable, if there is one.")
                  (.dataFetcher (reify DataFetcher
                                  (get [_ env]
                                    (let [obs (.getSource env)]
                                      (log/debug "Fetching verdict for: " (pr-str obs))
                                      (let [result (read-store
                                                    :verdict list-verdicts
                                                    {[:observable :type] (:type obs)
                                                     [:observable :value] (:value obs)}
                                                    {:sort_by :created
                                                     :sort_order :desc
                                                     :limit 1})]
                                        (log/debug "Verdict result: " (pr-str result))
                                        (first (:data result )))))))))
      
      ;; Lookup Judgements by Observables
      (.field (-> (GraphQLFieldDefinition/newFieldDefinition)
                  (.name "judgements")
                  add-connect-type-arguments
                  (.type JudgementConnectionType)
                  (.dataFetcher
                   (reify DataFetcher
                     (get [_ env]
                       (let [obs (.getSource env)
                             arguments (->clj (.getArguments env))
                             fields (->clj (.getFields env))
                             cursor (or (get arguments :after)
                                        (get arguments :before)
                                        "0")
                             offset (try (. Integer parseInt cursor)
                                         (catch Exception e 0)
                                         (finally 0))
                             limit (:first arguments)
                             query-params {:limit limit
                                           :offset offset}]
                         (log/debug "Fetching judgements with args: " (pr-str arguments))
                         (log/debug "Fetching judgements with fields: " (pr-str fields))
                         (log/debug "Fetching judgements for: " (pr-str obs))
                         (let [results (read-store :judgement
                                                   list-judgements-by-observable
                                                   obs
                                                   query-params)
                               total (get (:paging results) :total-hits 0)]
                           (log/debug "Results of fetch: " (pr-str results))
                           {:page-info {:total-hits (get (:paging results) :total-hits)
                                        :has-next-page (< (+ limit offset) total)
                                        :has-previous-page (and (:last arguments) (not (= offset 0)))}
                            :edges (map-indexed
                                    (fn [i n]
                                      {:cursor (+ i offset)
                                       :node n})
                                    (with-long-ids 
                                      :judgement (:data results)))})))))))
      (.build)))



(defn resolve-type [obj]
  (case (get obj :type)
    "judgement" JudgementType
    "sighting" SightingType
    "indicator" IndicatorType
    "relationship" RelationshipType))


(comment
  (read-store :judgement list-judgements-by-observable  {"limit" 10}))

(def QueryType
  (.. (GraphQLObjectType/newObject)
      (name "QueryType")
      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "observable")
                 (.type ObservableType)
                 (.argument (.. (GraphQLArgument/newArgument)
                               (name "type")
                               (description "The type of the Observable")
                               (type non-null-string)))
                 (.argument (.. (GraphQLArgument/newArgument)
                               (name "value")
                               (description "The value of the Observable")
                               (type non-null-string)))
                 (.dataFetcher (reify DataFetcher
                                (get [_ env]
                                  (->clj (.getArguments env)))))))
      
      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "judgement")
                 (.type JudgementType)
                 (.argument (.. (GraphQLArgument/newArgument)
                               (name "id")
                               (description "The ID of the Judgement")
                               (type non-null-string)))
                 (.dataFetcher (entity-by-id-fetcher :judgement read-judgement))))

      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "judgements")
                 (.type (list-type JudgementType))
                 add-connect-type-arguments
                 (.argument (.. (GraphQLArgument/newArgument)
                                (name "query")
                                (type graphql.Scalars/GraphQLString)
                                (description "a Lucene query string, will only return Judgements matching it.")))
                 (.dataFetcher (entity-search-fetcher :judgement))))
      
      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "indicator")
                 (.type IndicatorType)
                 (.argument (.. (GraphQLArgument/newArgument)
                               (name "id")
                               (description "The ID of the Indicator")
                               (type non-null-string)))
                 (.dataFetcher (entity-by-id-fetcher :indicator read-indicator))))

      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "indicators")
                 (.type (list-type IndicatorType))
                 add-connect-type-arguments
                 (.argument (.. (GraphQLArgument/newArgument)
                                (name "query")
                                (type graphql.Scalars/GraphQLString)
                                (description "a Lucene query string, will only return Indicators matching it.")))
                 (.dataFetcher (entity-search-fetcher :indicator))))

      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "sighting")
                 (.type SightingType)
                 (.argument (.. (GraphQLArgument/newArgument)
                               (name "id")
                               (description "The ID of the Sighting")
                               (type non-null-string)))
                 (.dataFetcher (entity-by-id-fetcher :sighting read-sighting))))

      (field (-> (GraphQLFieldDefinition/newFieldDefinition)
                 (.name "sightings")
                 (.type (list-type SightingType))
                 add-connect-type-arguments
                 (.argument (.. (GraphQLArgument/newArgument)
                                (name "query")
                                (type graphql.Scalars/GraphQLString)
                                (description "a Lucene query string, will only return Sightings matching it.")))
                 (.dataFetcher (entity-search-fetcher :sighting))))

      ))




(def schema (.. (GraphQLSchema/newSchema)
                (query QueryType)
                (build)))

(defn- doquery
  "A simple helper for testing here"
  ([qstr]
   (let [results (.execute (new GraphQL schema) qstr)
         errors (.getErrors results)
         data (.getData results)]
     {:data data
      :errors errors}))
  ([qstr varmap]
   (let [results (.execute (new GraphQL schema) qstr (cast String "testop") (cast Object {}) (cast java.util.Map varmap))
         errors (.getErrors results)
         data (.getData results)]
     {:data data
      :errors errors})))


(s/defschema RelayGraphQLQuery
  {:query s/Str
   (s/optional-key :variables) s/Any})

(s/defschema RelayGraphQLResponse
  {:data s/Any
   (s/optional-key :errors) [s/Any]})

(comment

  (new GraphQL schema)
  
  
  (doquery "query TestQuery { observable(type: \"ip\" value: $foo) { value }}" {"foo" 1})
  
  (doquery "query TestQuery {
            judgement(id: \"judgement-4c9e48bf-14a3-459c-a287-0939c394a3ac\") {
              id
              reason
              observable {
                type
                value
                judgements { edges { node { reason source } } }
              }
            }
          }")

  (doquery "query TestQuery {
            observable(type: \"domain\" value: \"static.duoshuo.com\") {
              value
              verdict { disposition }
              judgements(first: 1 after:\"0\") { pageInfo { totalHits hasNextPage } edges { node { reason source id } } }
            }
          }")

  (doquery "query SchemaQuery {__schema { types {name}}}"))
