(ns ctia.entity.relationship.schemas
  (:require
   [ctia.domain.entities
    :refer [default-realize-fn]]
   [flanders.utils :as fu]
   [ctim.schemas.relationship :as rels]
   [schema.core :as s]
   [ctia.graphql.delayed :as delayed]
   [ctia.schemas
    [utils :as csu]
    [core :refer [def-acl-schema
                  def-stored-schema
                  GraphQLRuntimeOptions
                  RealizeFnResult
                  lift-realize-fn-with-context]]
    [sorting :as sorting]]
   [ctia.flows.schemas :refer [with-error]]
   [clojure.string :as string]))

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

(def-stored-schema StoredRelationship Relationship)

(s/defschema PartialStoredRelationship
  (csu/optional-keys-schema StoredRelationship))

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
  :- (RealizeFnResult (with-error StoredRelationship))
  [{:keys [source_ref
           target_ref]
    :as new-entity}
   id
   tempids
   & rest-args]
 (delayed/fn :- (with-error StoredRelationship)
  [rt-opt :- GraphQLRuntimeOptions]
  (let [e (-> relationship-default-realize 
              (lift-realize-fn-with-context rt-opt)
              (apply new-entity id tempids rest-args)
              (assoc :source_ref (get tempids source_ref source_ref)
                     :target_ref (get tempids target_ref target_ref)))]
    (if (or (string/starts-with? (:source_ref e) "transient:")
            (string/starts-with? (:target_ref e) "transient:"))
      {:id id
       :error (str "A relationship cannot be created if a source "
                   "or a target ref is still a transient ID "
                   "(The source or target entity is probably not "
                   "provided in the bundle)")
       :type :realize-entity-error}
      e))))
