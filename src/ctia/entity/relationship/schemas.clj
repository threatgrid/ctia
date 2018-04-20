(ns ctia.entity.relationship.schemas
  (:require
   [ctia.domain.entities
    :refer [default-realize-fn]]
   [flanders.utils :as fu]
   [ctim.schemas.relationship :as rels]
   [schema.core :as s]
   [ctia.schemas
    [core :refer [def-acl-schema
                  def-stored-schema]]
    [sorting :as sorting]]))

(def-acl-schema Relationship
  rels/Relationship
  "relationship")

(def-acl-schema PartialRelationship
  (fu/optionalize-all rels/Relationship)
  "partial-relationship")

(s/defschema PartialRelationshipList
  [PartialRelationship])

(def-acl-schema NewRelationship
  rels/NewRelationship
  "new-relationship")

(def-stored-schema StoredRelationship
  rels/StoredRelationship
  "stored-relationship")

(def-stored-schema PartialStoredRelationship
  (fu/optionalize-all rels/StoredRelationship)
  "partial-stored-relationship")

(def relationship-default-realize
  (default-realize-fn "relationship" NewRelationship StoredRelationship))

(def relationship-fields
  (concat sorting/default-entity-sort-fields
          sorting/describable-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:relationship_type
           :source_ref
           :target_ref]))

(s/defn realize-relationship
  :- StoredRelationship
  [{:keys [source_ref
           target_ref]
    :as new-entity}
   id
   tempids
   & rest-args]
  (assoc (apply relationship-default-realize new-entity id tempids rest-args)
         :source_ref (get tempids source_ref source_ref)
         :target_ref (get tempids target_ref target_ref)))

