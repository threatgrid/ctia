(ns ctia.schemas.graphql
  (:require [clojure.tools.logging :as log])
  (:import graphql.GraphQL)
  (:import (graphql.schema GraphQLArgument
                           GraphQLInterfaceType
                           GraphQLObjectType
                           GraphQLList
                           GraphQLFieldDefinition
                           GraphQLSchema
                           GraphQLTypeReference
                           DataFetcher
                           TypeResolver
                           
                           GraphQLNonNull))
  (:import (graphql.Scalars))
  (:require [ctia.store :refer :all]
            [schema.core :as s]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :refer [factory:short-id->long-id]]))

;; TODOS
;; - Complete Judgmeent schema
;; - Add sighting and indicator lookup to Observable type
;; - Relationship type
;; - Search query support
;; - Remaining types
;; - Feedback support



(defn gql-field [fname ftype description fetcher]
  (.. (GraphQLFieldDefinition/newFieldDefinition)
      (name fname)
      (type ftype)
      (description description)
      (dataFetcher  fetcher)))

(def non-null-string (new GraphQLNonNull graphql.Scalars/GraphQLString))


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


(defn map-fetcher [key]
  (reify DataFetcher
    (get [_ env]
      (let [obj (.getSource env)]
        (log/info "map-fetcher called on: " (pr-str obj) " key: " (pr-str key))
        (get obj key)))))

(defn static-fetcher [value]
  (reify DataFetcher
    (get [_ env]
      (log/debug "static-fetcher called: " value)
      value)))

(defn entity-read-fetcher [entity read-fn]
  (reify DataFetcher
    (get [_ env]
      (let [id (.getArgument env "id")
            ext_id (.getArgument env "external_id")]
        (log/info "entity-read-fetcher called: " (pr-str id))
        (update (read-store entity read-fn id)
                :id (factory:short-id->long-id :judgement get-http-show))))))

(defn with-long-ids [entity l]
  (let [f (factory:short-id->long-id entity get-http-show)
        t (fn [o]
            (update o :id f))]
    (map t l)))

(defn entity-search-fetcher [entity search-fn]
  (reify DataFetcher
    (get [_ env]
      (let [args (->clj (.getArguments env))]
        (log/info "entity-read-fetcher called: " (pr-str args))
        (query-string-search-store :entity search-fn args
                                   (:query args)
                                   (dissoc args :query :sort_by :sort_order :offset :limit)
                                   (select-keys args [:sort_by :sort_order :offset :limit]))))))


(def id-field (gql-field "id" non-null-string "The ID of the object" (map-fetcher :id)))



(defn add-base-entity-fields
  "Given an object builders, adds all of the BaseEntity fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (gql-field "id"
                         graphql.Scalars/GraphQLID
                         "ID of the object"
                         (map-fetcher :id)))
      (.field (gql-field "type"
                         non-null-string
                         "type of the object"
                         (map-fetcher :type)))
      (.field (gql-field "schema_version"
                         non-null-string
                         "schema_version of the object"
                         (map-fetcher :type)))
      (.field (gql-field "revision"
                         graphql.Scalars/GraphQLInt
                         "revision of the object"
                         (map-fetcher :revision)))
      (.field (gql-field "external_ids"
                         (new GraphQLList (graphql.Scalars/GraphQLString))
                         "a list of external IDs other systems use to refer to this object"
                         (map-fetcher :external_ids)))
      (.field (gql-field "timestamp"
                         graphql.Scalars/GraphQLString
                         "an ISO8601 timestamp for when the object was defined"
                         (map-fetcher :timestamp)))
      (.field (gql-field "language"
                         graphql.Scalars/GraphQLString
                         "optional language identifier for the human language of the object"
                         (map-fetcher :language)))
      (.field (gql-field "tlp"
                         graphql.Scalars/GraphQLString
                         "Traffic Light Protocol value"
                         (map-fetcher :tlp)))))

(defn add-describable-entity-fields
  "Given an object builders, adds all of the DescribableEntity fields, with the default data fetcher"
  [builder]
  (-> builder
      (.field (gql-field "title"
                         graphql.Scalars/GraphQLString
                         "Title"
                         (map-fetcher :id)))
      (.field (gql-field "short_description"
                         graphql.Scalars/GraphQLString
                         "Short Description of the object"
                         (map-fetcher :short_description)))
      (.field (gql-field "description"
                         graphql.Scalars/GraphQLString
                         "Full Description of the object, Markdown text supported."
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
                         "A URI for more information about the object, from the source"
                         (map-fetcher :source_uri)))))

(def entity-type-resolver  (reify TypeResolver
                             (getType [_ obj]
                               (case (get obj :type)
                                 "judgement" (new GraphQLTypeReference "Judgement")
                                 "sighting" (new GraphQLTypeReference "Sighting")
                                 "indicator" (new GraphQLTypeReference "Indicator")
                                 "relationship" (new GraphQLTypeReference "Relationship")
                                 ))))

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

(def JudgementType
  (-> (GraphQLObjectType/newObject)
      (.name "Judgement")
      (.withInterface BaseEntityInterface)
      ;;(.withInterface SourcedObjectInterface)
      (add-base-entity-fields)
      (add-sourced-object-fields)
      (.field (gql-field "reason"
                         graphql.Scalars/GraphQLString
                         "The reason the judgement was made."
                         (map-fetcher :reason)))
      (.field (gql-field "severity"
                         graphql.Scalars/GraphQLString
                         "The severity of the threat"
                         (map-fetcher :severity)))
      (.field (gql-field "confidence"
                         graphql.Scalars/GraphQLString
                         "The confidence with which the judgement was made."
                         (map-fetcher :confidence)))
      (.field (gql-field "reason_uri"
                         graphql.Scalars/GraphQLString
                         "A URI where details on the reason are available."
                         (map-fetcher :reasoun_uri)))
      (.field (gql-field "observable"
                         (new GraphQLTypeReference "Observable")
                         "The Observable this judgement pertains too"
                         (map-fetcher :observable)))
      (.build)))


(def VerdictType
    (-> (GraphQLObjectType/newObject)
      (.name "Verdict")
      (.field (gql-field "id"
                         graphql.Scalars/GraphQLID
                         "ID of the object"
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
                         (map-fetcher :reason)))
      (.field (gql-field "disposition"
                         graphql.Scalars/GraphQLInt
                         "The disposition numeric identifier for the disposition of the observable"
                         (map-fetcher :reason)))
      (.field (gql-field "judgement"
                         graphql.Scalars/GraphQLString
                         "The confidence with which the judgement was made."
                         (reify DataFetcher
                           (get [_ env]
                             (read-store
                              :verdict read-judgement
                              (:judgement_id (.getSource env))
                              )))))
      (.field (gql-field "reason_uri"
                         graphql.Scalars/GraphQLString
                         "A URI where details on the reason are available."
                         (map-fetcher :reasoun_uri)))
      (.field (gql-field "observable"
                         (new GraphQLTypeReference "Observable")
                         "The Observable this judgement pertains too"
                         (map-fetcher :observable)))
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
                     (name "after")
                     (type graphql.Scalars/GraphQLString)
                     (description "start from this object")))))

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
                                      (log/info "Fetching verdict for: " (pr-str obs))
                                      (let [result (read-store
                                                     :verdict list-verdicts
                                                     obs
                                                     {:sort_by :created
                                                      :sort_order :desc
                                                      :limit 1})]
                                        (log/info "Verdict result: " (pr-str result))
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
                         (log/info "Fetching judgements with args: " (pr-str arguments))
                         (log/info "Fetching judgements with fields: " (pr-str fields))
                         (log/info "Fetching judgements for: " (pr-str obs))
                         (let [results (read-store :judgement
                                                   list-judgements-by-observable
                                                   obs
                                                   query-params)
                               total (get (:paging results) :total-hits 0)]
                           (log/info "Results of fetch: " (pr-str results))
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




(comment
  (read-store :judgement list-judgements-by-observable  {"limit" 10}))

(def QueryType
  (.. (GraphQLObjectType/newObject)
      (name "QueryType")
      (field (.. (GraphQLFieldDefinition/newFieldDefinition)
                 (name "observable")
                 (type ObservableType)
                 (argument (.. (GraphQLArgument/newArgument)
                               (name "type")
                               (description "The type of the Observable")
                               (type non-null-string)))
                 (argument (.. (GraphQLArgument/newArgument)
                               (name "value")
                               (description "The value of the Observable")
                               (type non-null-string)))
                 (dataFetcher (reify DataFetcher
                                (get [_ env]
                                  (->clj (.getArguments env)))))))
      
      (field (.. (GraphQLFieldDefinition/newFieldDefinition)
                 (name "judgement")
                 (type JudgementType)
                 (argument (.. (GraphQLArgument/newArgument)
                               (name "id")
                               (description "The ID of the Judgement")
                               (type non-null-string)))
                 (dataFetcher (entity-read-fetcher :judgement read-judgement))))

      
      (field (.. (GraphQLFieldDefinition/newFieldDefinition)
                 (name "judgements")
                 (type (new GraphQLList JudgementType))
                 (argument (.. (GraphQLArgument/newArgument)
                               (name "external_id")
                               (description "An external ID of the Judgement")
                               (type non-null-string)))
                 (dataFetcher (entity-read-fetcher :judgement list-judgements))))))


(def schema (.. (GraphQLSchema/newSchema)
                (query QueryType)
                (build)))

(defn doquery [qstr]
  (let [results (.execute (new GraphQL schema) qstr)
        errors (.getErrors results)
        data (.getData results)]
    {:data data
     :errors errors}))


(s/defschema RelayGraphQLQuery
  {:query s/Str
   :variables {s/Str s/Any}})

(s/defschema RelayGraphQLResponse
  {:data {s/Str s/Any}
   :errors [s/Str]})

(comment

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
