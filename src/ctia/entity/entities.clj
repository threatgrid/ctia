(ns ctia.entity.entities
  (:refer-clojure :exclude [identity])
  (:require [ctia.entity
             [actor :refer [actor-entity]]
             [attack-pattern :refer [attack-pattern-entity]]
             [campaign :refer [campaign-entity]]
             [casebook :refer [casebook-entity]]
             [coa :refer [coa-entity]]
             [data-table :refer [data-table-entity]]
             [exploit-target :refer [exploit-target-entity]]
             [feedback :refer [feedback-entity]]
             [incident :refer [incident-entity]]
             [indicator :refer [indicator-entity]]
             [investigation :refer [investigation-entity]]
             [judgement :refer [judgement-entity]]
             [malware :refer [malware-entity]]
             [relationship :refer [relationship-entity]]
             [sighting :refer [sighting-entity]]
             [tool :refer [tool-entity]]
             [identity :refer [identity-entity]]
             [event :refer [event-entity]]]))

(def entities
  {:actor actor-entity
   :attack-pattern attack-pattern-entity
   :campaign campaign-entity
   :casebook casebook-entity
   :coa coa-entity
   :data-table data-table-entity
   :exploit-target exploit-target-entity
   :feedback feedback-entity
   :incident incident-entity
   :indicator indicator-entity
   :investigation investigation-entity
   :judgement judgement-entity
   :malware malware-entity
   :relationship relationship-entity
   :sighting sighting-entity
   :tool tool-entity

   :identity identity-entity
   :event event-entity})
