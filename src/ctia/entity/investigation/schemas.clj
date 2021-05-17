(ns ctia.entity.investigation.schemas
  (:require
   [ctia.entity.investigation.flanders-schemas :as f-inv]
   [ctia.domain.entities :refer [default-realize-fn]]
   [flanders.utils :as fu]
   [ctia.schemas.core :refer [def-advanced-acl-schema def-stored-schema]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(def-advanced-acl-schema
  {:name-sym Investigation
   :ddl f-inv/Investigation
   :spec-kw-ns "investigation"
   :open? true})

(def-advanced-acl-schema
  {:name-sym PartialInvestigation
   :ddl (fu/optionalize-all f-inv/Investigation)
   :spec-kw-ns "partial-investigation"
   :open? true})

(s/defschema PartialInvestigationList
  [PartialInvestigation])

(def-advanced-acl-schema
  {:name-sym NewInvestigation
   :ddl f-inv/NewInvestigation
   :spec-kw-ns "new-investigation"
   :open? true})

(def-stored-schema StoredInvestigation Investigation)

(s/defschema PartialStoredInvestigation
  (st/optional-keys-schema StoredInvestigation))

(def realize-investigation
  (default-realize-fn "investigation"
                      NewInvestigation
                      StoredInvestigation))
