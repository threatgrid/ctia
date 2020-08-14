(ns ctia.entity.sighting.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [entities :refer [default-realize-fn]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema TempIDs
                           MaybeDelayedRealizeFnResult
                           MaybeDelayedRealizeFn->RealizeFn]]
             [sorting :as sorting]]
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
  (csu/optional-keys-schema StoredSighting))

(def sighting-default-realize
  (default-realize-fn "sighting" NewSighting StoredSighting))

(s/defn realize-sighting :- (MaybeDelayedRealizeFnResult StoredSighting)
  ([new-sighting id tempids owner groups]
   (realize-sighting new-sighting id tempids owner groups nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-sighting :- (s/maybe StoredSighting)]
  (s/fn :- StoredSighting
   [rt-opt]
   ((MaybeDelayedRealizeFn->RealizeFn sighting-default-realize rt-opt)
    (assoc new-sighting
           :count (:count new-sighting
                          (:count prev-sighting 1))
           :confidence (:confidence new-sighting
                                    (:confidence prev-sighting "Unknown")))
    id tempids owner groups prev-sighting))))

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
