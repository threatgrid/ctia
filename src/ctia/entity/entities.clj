(ns ctia.entity.entities
  (:refer-clojure :exclude [identity])
  (:require
   ;; !!! Order Matters !!!
   [clojure.tools.logging :as log]
   [ctia.schemas.core :refer [Entity]]
   [ctia.entity
    [asset :refer [asset-entity]]
    [feedback :refer [feedback-entity]]
    [weakness :refer [weakness-entity]]
    [vulnerability :refer [vulnerability-entity]]
    [attack-pattern :refer [attack-pattern-entity]]
    [indicator :refer [indicator-entity]]
    [incident :refer [incident-entity]]
    [malware :refer [malware-entity]]
    [tool :refer [tool-entity]]
    [sighting :refer [sighting-entity]]
    [judgement :refer [judgement-entity]]
    [casebook :refer [casebook-entity]]
    [identity-assertion :refer [identity-assertion-entity]]
    [actor :refer [actor-entity]]
    [campaign :refer [campaign-entity]]
    [coa :refer [coa-entity]]
    [data-table :refer [data-table-entity]]
    [relationship :refer [relationship-entity]]
    [identity :refer [identity-entity]]
    [feed :refer [feed-entity]]
    [event :refer [event-entity]]
    [investigation :refer [investigation-entity]]]
   [schema.core :as s]))

(def entities
  {:actor actor-entity
   :asset asset-entity
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
   :feed feed-entity
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
