(ns ctia.schemas.sorting)

(def base-entity-sort-fields [:id :schema_version :revision
                              :timestamp :language :tlp])

(def sourcable-entity-sort-fields [:source :source_uri])

(def describable-entity-sort-fields [:title])

(def default-entity-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          describable-entity-sort-fields))


(def exploit-target-sort-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time]))

(def incident-sort-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :confidence
           :status
           :incident_time
           :reporter
           :coordinator
           :victim
           :security_compromise
           :discovery_method
           :contact
           :intended_effect]))

(def indicator-sort-fields
  (concat default-entity-sort-fields
          [:indicator_type
           :likely_impact
           :confidence
           :specification]))

(def relationship-sort-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields
          [:relationship_type
           :source_ref
           :target_ref]))

(def judgement-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          [:disposition
           :priority
           :confidence
           :severity
           :valid_time.start_time
           :valid_time.end_time
           :reason
           :observable.type
           :observable.value]))

(def sighting-sort-fields
  (concat default-entity-sort-fields
          [:observed_time.start_time
           :observed_time.end_time
           :confidence
           :count
           :sensor]))

(def ttp-sort-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :ttp_type]))

(def actor-sort-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :actor_type
           :motivation
           :sophistication
           :intended_effect]))

(def campaign-sort-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :campaign_type
           :status
           :activity
           :confidence]))

(def coa-sort-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :stage
           :coa_type
           :impact
           :cost
           :efficacy
           :structured_coa_type]))

(def feedback-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          [:entity_id
           :feedback
           :reason]))
