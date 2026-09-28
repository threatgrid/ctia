(ns ctia.entity.relationship.es-store
  (:require [ctia.entity.relationship.schemas :as rs]
            [ctia.domain.entities :refer [long-id->id]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store StoreOpts]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def relationship-mapping
  {"relationship"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:relationship_type em/token
      :source_ref        em/token
      :target_ref        em/token
      :source_type       em/token
      :target_type       em/token})}})

(s/defschema ESStoredRelationship
  (st/merge rs/StoredRelationship
            (st/optional-keys
             {:source_type s/Str
              :target_type s/Str})))

(s/defschema ESPartialStoredRelationship
  (st/merge rs/PartialStoredRelationship
            (st/optional-keys
             {:source_type s/Str
              :target_type s/Str})))

(def ESPartialStoredRelationshipList (list-response-schema ESPartialStoredRelationship))
(def PartialStoredRelationshipList (list-response-schema rs/PartialStoredRelationship))

(s/defn stored-relationship->es-stored-relationship
  :- ESStoredRelationship
  "adds source and target types to a relationship"
  [{:keys [source_ref target_ref] :as r} :- rs/StoredRelationship]
  (let [source-type (:type (long-id->id source_ref))
        target-type (:type (long-id->id target_ref))]
    (cond-> r
      source-type (assoc :source_type source-type)
      target-type (assoc :target_type target-type))))

(s/defn es-stored-relationship->stored-relationship
  :- ESStoredRelationship
  "dissoc source and target types to a relationship"
  [{:keys [source_ref target_ref] :as r} :- ESStoredRelationship]
  (dissoc r :source_type :target_type))

(s/defn es-partial-stored-relationship->partial-stored-relationship
  :- rs/PartialStoredRelationship
  "dissoc source and target types to a relationship"
  [r :- ESPartialStoredRelationship]
  (dissoc r :source_type :target_type))

(s/def store-opts :- StoreOpts
  {:stored->es-stored (comp stored-relationship->es-stored-relationship :doc)
   :es-stored->stored (comp es-stored-relationship->stored-relationship :doc)
   :es-partial-stored->partial-stored (comp es-partial-stored-relationship->partial-stored-relationship :doc)
   :es-stored-schema ESStoredRelationship
   :es-partial-stored-schema ESPartialStoredRelationship})

(def-es-store RelationshipStore
  :relationship
  rs/StoredRelationship
  rs/PartialStoredRelationship
  :store-opts store-opts
  )
