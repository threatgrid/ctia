(ns ctia.schemas.graphql.relationship
  (:require
   [flanders.utils :as fu]
   [clojure.tools.logging :as log]
   [ctia.schemas.graphql
    [common :as common]
    [flanders :as f]
    [helpers :as g]
    [pagination :as p]
    [refs :as refs]
    [resolvers :as res
     :refer [entity-by-id search-relationships]]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.domain.id :as id]
   [ctim.schemas
    [indicator :as ctim-ind]
    [judgement :as ctim-j]
    [relationship :as ctim-rel]
    [sighting :as ctim-sig]
    [vocabularies :as ctim-voc]]
   [schema.core :as s])
  (:import graphql.Scalars))

(def related-judgement-fields
  {:judgement {:type refs/JudgementRef
               :description "The related Judgement"
               :resolve (fn [context _ field-selection src]
                          (when-let [id (:judgement_id src)]
                            (res/judgement-by-id id (:ident context) field-selection)))}})

(def RelatedJudgement
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-rel/RelatedJudgement))]
    (g/new-object name description [] (into fields
                                            related-judgement-fields))))

(s/defn ref->entity-type :- s/Str
  "Extracts the entity type from the Reference"
  [ref :- s/Str]
  (some-> ref
          id/long-id->id
          :type))

(def Entity
  (g/new-union
   "Entity"
   ""
   (fn [obj args schema]
     (log/debug "Entity resolution" obj args)
     (condp = (:type obj)
       ctim-j/type-identifier (g/get-type schema refs/judgement-type-name)
       ctim-ind/type-identifier (g/get-type schema refs/indicator-type-name)
       ctim-sig/type-identifier (g/get-type schema refs/sighting-type-name)))
   [refs/JudgementRef
    refs/IndicatorRef
    refs/SightingRef]))

(def relation-fields
  (merge
   (g/non-nulls
    {:source_entity {:type Entity
                     :resolve (fn [context args field-selection src]
                                (log/debug "Source resolver" args src)
                                (let [ref (:source_ref src)
                                      entity-type (ref->entity-type ref)]
                                  (entity-by-id entity-type
                                                ref
                                                (:ident context)
                                                field-selection)))}
     :target_entity {:type Entity
                     :resolve (fn [context args field-selection src]
                                (log/debug "Target resolver" args src)
                                (let [ref (:target_ref src)
                                      entity-type (ref->entity-type ref)]
                                  (entity-by-id entity-type
                                                ref
                                                (:ident context)
                                                field-selection)))}})))

(def RelationshipType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-rel/Relationship)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name
                  description
                  []
                  (merge fields
                         relation-fields))))

(def relationship-order-arg
  (sorting/order-by-arg
   "RelationshipOrder"
   "relationships"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/relationship-sort-fields))))

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
