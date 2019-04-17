(ns ctia.entity.sighting.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [default-realize-fn]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema TempIDs]]
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

(s/defn realize-sighting :- StoredSighting
  ([new-sighting id tempids owner groups]
   (realize-sighting new-sighting id tempids owner groups nil))
  ([new-sighting id tempids owner groups prev-sighting]
   (let [now (time/now)]
     (sighting-default-realize
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
