(ns ctia.entity.entities
  (:refer-clojure :exclude [identity])
  (:require
   ;; !!! Order Matters !!!
   [clojure.tools.logging :as log]
   [ctia.schemas.core :refer [Entity]]
   [ctia.entity
    [weakness :refer [weakness-entity]]
    [vulnerability :refer [vulnerability-entity]]
    [attack-pattern :refer [attack-pattern-entity]]
    [indicator :refer [indicator-entity]]
    [investigation :refer [investigation-entity]]
    [casebook :refer [casebook-entity]]
    [judgement :refer [judgement-entity]]
    [malware :refer [malware-entity]]
    [sighting :refer [sighting-entity]]
    [identity-assertion :refer [identity-assertion-entity]]
    [tool :refer [tool-entity]]
    [actor :refer [actor-entity]]
    [campaign :refer [campaign-entity]]
    [coa :refer [coa-entity]]
    [data-table :refer [data-table-entity]]
    [feedback :refer [feedback-entity]]
    [incident :refer [incident-entity]]
    [relationship :refer [relationship-entity]]
    [identity :refer [identity-entity]]
    [event :refer [event-entity]]]
   [schema.core :as s]))

(def entities
  {:actor actor-entity
   :attack-pattern attack-pattern-entity
   :campaign campaign-entity
   :casebook casebook-entity
   :coa coa-entity
   :data-table data-table-entity
   :feedback feedback-entity
   :incident incident-entity
   :indicator indicator-entity
   :investigation investigation-entity
   :judgement judgement-entity
   :malware malware-entity
   :relationship relationship-entity
   :sighting sighting-entity
   :identity-assertion identity-assertion-entity
   :tool tool-entity
   :vulnerability vulnerability-entity
   :weakness weakness-entity

   :identity identity-entity
   :event event-entity})

(defn validate-entities []
  (doseq [[entity entity-map] entities]
    (try
      (s/validate Entity entity-map)
      (catch Exception e
        (if-let [errors (some->> (ex-data e)
                                 :error
                                 (remove nil?))]
          (let [message
                (format (str "%s definition is invalid, "
                             "errors: %s")
                        entity
                        (pr-str errors))]
            (log/error message)
            message)
          (throw e))))))
