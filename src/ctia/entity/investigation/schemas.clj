(ns ctia.entity.investigation.schemas
  (:require
   [ctia.entity.investigation.flanders-schemas :as f-inv]
   [ctia.domain.entities :refer [default-realize-fn]]
   [flanders.utils :as fu]
   [ctia.schemas
    [utils :as csu]
    [core :refer [def-acl-schema
                  def-stored-schema]]]
   [schema.core :as s]))

(def-acl-schema Investigation f-inv/Investigation "investigation")

(def-acl-schema PartialInvestigation
  (fu/optionalize-all f-inv/Investigation)
  "partial-investigation")

(s/defschema PartialInvestigationList
  [PartialInvestigation])

(def-acl-schema NewInvestigation f-inv/NewInvestigation "new-investigation")

(def-stored-schema StoredInvestigation Investigation)

(s/defschema PartialStoredInvestigation
  (csu/optional-keys-schema StoredInvestigation))

(def realize-investigation
  (default-realize-fn "investigation"
                      NewInvestigation
                      StoredInvestigation))
