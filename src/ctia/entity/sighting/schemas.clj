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
                     TempIDs]]
            [ctia.schemas.sorting :as sorting]
            [ctim.schemas.sighting :as ss]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema NewSighting
  ss/NewSighting
  "new-sighting")

(def-acl-schema Sighting
  ss/Sighting
  "sighting")

(def-acl-schema PartialSighting
  (fu/optionalize-all ss/Sighting)
  "partial-sighting")

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
