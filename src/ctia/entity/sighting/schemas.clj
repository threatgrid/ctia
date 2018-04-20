(ns ctia.entity.sighting.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [schema-version]]]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema TempIDs]]
             [sorting :as sorting]]
            [ctim.schemas.sighting :as ss]
            [flanders.utils :as fu]
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

(def-stored-schema StoredSighting
  ss/StoredSighting
  "stored-sighting")

(def-stored-schema PartialStoredSighting
  (fu/optionalize-all ss/StoredSighting)
  "partial-stored-sighting")

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]]
   (realize-sighting new-sighting id tempids owner groups nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (time/now)]
     (assoc new-sighting
            :id id
            :type "sighting"
            :owner (or (:owner prev-sighting) owner)
            :groups (or (:groups prev-sighting) groups)
            :count (:count new-sighting
                           (:count prev-sighting 1))
            :confidence (:confidence new-sighting
                                     (:confidence prev-sighting "Unknown"))
            :tlp (:tlp new-sighting
                       (:tlp prev-sighting (properties-default-tlp)))
            :schema_version schema-version
            :created (or (:created prev-sighting) now)
            :modified now))))

(def sighting-fields
  (concat sorting/default-entity-sort-fields
          [:observed_time.start_time
           :observed_time.end_time
           :confidence
           :count
           :sensor]))
