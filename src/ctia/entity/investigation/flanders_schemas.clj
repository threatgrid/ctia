(ns ctia.entity.investigation.flanders-schemas
  (:require [ctim.schemas.common :as c]
            [flanders.core :as f :refer [def-entity-type def-eq]]
            [schema.core :as s]))

(def type-identifier "investigation")

(def-eq InvestigationIdentifier type-identifier)

(def-entity-type Investigation
  "Schema for an Investigation (a work in progress)"
  c/base-entity-entries
  c/sourced-object-entries
  c/describable-entity-entries
  (f/required-entries
   (f/entry :type InvestigationIdentifier))
  (f/optional-entries
   (f/entry :actions f/any-str
            :description "Investigation actions encoded as JSON (an array of objects).")
   (f/entry :object_ids f/any-string-seq)
   (f/entry :investigated_observables f/any-string-seq)
   (f/entry :targets (f/seq-of c/IdentitySpecification)
            :description "Investigated target devices")))

(def-entity-type NewInvestigation
  "Schema for submitting new Investigations"
  (:entries Investigation)
  c/base-new-entity-entries
  (f/optional-entries
   (f/entry :type InvestigationIdentifier)))
