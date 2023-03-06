(ns ctia.entity.sighting.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.auth :as auth]
            [ctia.graphql.delayed :as delayed]
            [ctia.schemas.core
             :refer [def-acl-schema
                     def-stored-schema
                     GraphQLRuntimeContext
                     lift-realize-fn-with-context
                     RealizeFnResult
                     TempIDs
                     CTIAEntity]]
            [ctia.schemas.sorting :as sorting]
            [ctim.schemas.sighting :as ss]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]
            [flanders.schema :as f-schema]
            [flanders.spec :as f-spec]))

(s/defschema NewSighting
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     ss/NewSighting))
   CTIAEntity))

(f-spec/->spec ss/NewSighting "new-sighting")

(s/defschema Sighting
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     ss/Sighting))
   CTIAEntity))

(f-spec/->spec ss/Sighting "sighting")

(s/defschema PartialSighting
  (st/merge CTIAEntity
            (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               ss/Sighting)))))

(s/defschema PartialSightingList
  [PartialSighting])

(def-stored-schema StoredSighting Sighting)

(s/defschema PartialStoredSighting
  (st/optional-keys-schema StoredSighting))

(def sighting-default-realize
  (default-realize-fn "sighting" NewSighting StoredSighting))

(s/defn realize-sighting :- (RealizeFnResult StoredSighting)
  ([new-sighting id tempids ident-map]
   (realize-sighting new-sighting id tempids ident-map nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    ident-map :- auth/IdentityMap
    prev-sighting :- (s/maybe StoredSighting)]
   (delayed/fn :- StoredSighting
     [rt-ctx :- GraphQLRuntimeContext]
     ((lift-realize-fn-with-context sighting-default-realize rt-ctx)
      (assoc new-sighting
             :count (:count new-sighting
                            (:count prev-sighting 1))
             :confidence (:confidence new-sighting
                                      (:confidence prev-sighting "Unknown")))
      id tempids ident-map prev-sighting))))

(def sighting-fields
  (concat sorting/default-entity-sort-fields
          [:observed_time.start_time
           :observed_time.end_time
           :confidence
           :count
           :sensor]))

(def sighting-histogram-fields
  [:timestamp
   :observed_time.start_time
   :observed_time.end_time])

(def sighting-enumerable-fields
  [:source
   :sensor
   :observables.type])

(def sighting-sort-fields
  (conj sighting-fields
        "observed_time.start_time,timestamp"))
