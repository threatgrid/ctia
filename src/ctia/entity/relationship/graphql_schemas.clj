(ns ctia.entity.relationship.graphql-schemas
  (:require [clojure.tools.logging :as log]
            [ctia.entity.relationship.schemas :as rs]
            [ctia.schemas.graphql
             [common :as common]
             [flanders :as f]
             [helpers :as g]
             [pagination :as p]
             [refs :as refs]
             [resolvers :as res :refer [entity-by-id
                                        search-relationships]]
             [sorting :as sorting]]
            [ctim.domain.id :as id]
            [ctim.schemas
             [attack-pattern :as ctim-ap]
             [incident :as ctim-inc]
             [indicator :as ctim-ind]
             [judgement :as ctim-j]
             [malware :as ctim-malw]
             [relationship :as ctim-rel]
             [sighting :as ctim-sig]
             [tool :as ctim-tool]
             [vulnerability :as ctim-vul]
             [weakness :as ctim-weak]]
            [flanders.utils :as fu]
            [schema.core :as s]
            [ctia.schemas.graphql.ownership :as go])
  (:import graphql.Scalars))

(def related-judgement-fields
  {:judgement {:type refs/JudgementRef
               :description "The related Judgement"
               :resolve (fn [context _ field-selection src]
                          (when-let [id (:judgement_id src)]
                            (res/entity-by-id :judgement
                                              id
                                              (:ident context)
                                              field-selection)))}})

(def RelatedJudgement
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-rel/RelatedJudgement))]
    (g/new-object name description [] (into fields
                                            related-judgement-fields))))

(s/defn ref->entity-type :- (s/maybe s/Keyword)
  "Extracts the entity type from the Reference"
  [ref :- s/Str]
  (some-> ref
          id/long-id->id
          :type
          keyword))

(def Entity
  (g/new-union
   "Entity"
   ""
   (fn [obj args schema]
     (log/debug "Entity resolution" obj args)
     (condp = (:type obj)
       ctim-ap/type-identifier (g/get-type schema refs/attack-pattern-type-name)
       ctim-j/type-identifier (g/get-type schema refs/judgement-type-name)
       ctim-malw/type-identifier (g/get-type schema refs/malware-type-name)
       ctim-inc/type-identifier (g/get-type schema refs/incident-type-name)
       ctim-ind/type-identifier (g/get-type schema refs/indicator-type-name)
       ctim-sig/type-identifier (g/get-type schema refs/sighting-type-name)
       ctim-tool/type-identifier (g/get-type schema refs/tool-type-name)
       ctim-vul/type-identifier (g/get-type schema refs/vulnerability-type-name)
       ctim-weak/type-identifier (g/get-type schema refs/weakness-type-name)))
   [refs/AttackPatternRef
    refs/JudgementRef
    refs/MalwareRef
    refs/IncidentRef
    refs/IndicatorRef
    refs/SightingRef
    refs/ToolRef
    refs/VulnerabilityRef
    refs/WeaknessRef]))

(def relation-fields
  {:source_entity {:type Entity
                   :resolve (fn [context args field-selection src]
                              (log/debug "Source resolver" args src)
                              (let [ref (:source_ref src)
                                    entity-type (ref->entity-type ref)]
                                (when entity-type
                                  (entity-by-id entity-type
                                                ref
                                                (:ident context)
                                                field-selection))))}
   :target_entity {:type Entity
                   :resolve (fn [context args field-selection src]
                              (log/debug "Target resolver" args src)
                              (let [ref (:target_ref src)
                                    entity-type (ref->entity-type ref)]
                                (when entity-type
                                  (entity-by-id entity-type
                                                ref
                                                (:ident context)
                                                field-selection))))}})

(def RelationshipType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-rel/Relationship)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name
                  description
                  []
                  (merge fields
                         relation-fields
                         go/graphql-ownership-fields))))

(def relationship-order-arg
  (sorting/order-by-arg
   "RelationshipOrder"
   "relationships"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              rs/relationship-fields))))

(def RelationshipConnectionType
  (p/new-connection RelationshipType))

(def relatable-entity-fields
  {:relationships
   {:type RelationshipConnectionType
    :description (str "A connection containing the Relationship objects "
                      "that has this object a their source.")
    :args
    (merge
     common/lucene-query-arguments
     go/graphql-ownership-fields
     {:relationship_type
      {:type Scalars/GraphQLString
       :description (str "restrict to Relations with the specified relationship_type.")}
      :target_type
      {:type Scalars/GraphQLString
       :description (str "restrict to Relationships whose target is of the "
                         "specified CTIM entity type.")}}
     p/connection-arguments
     relationship-order-arg)
    :resolve search-relationships}})
