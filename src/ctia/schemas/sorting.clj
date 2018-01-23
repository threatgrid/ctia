(ns ctia.schemas.sorting)

;; These are the fields that are sortable, per entity.  We place them
;; here since they are used by more than one entity's routes.  For
;; isntance, the indicator route needs to know how to sort sightings
;; for the `ctia/indicator/:ID/sighting` handler
;;
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
           :incident_time.first_malicious_action
           :incident_time.initial_compromise
           :incident_time.first_data_exfiltration
           :incident_time.incident_discovery
           :incident_time.incident_opened
           :incident_time.containment_achieved
           :incident_time.restoration_achieved
           :incident_time.incident_reported
           :incident_time.incident_closed
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
           :confidence]))

(def investigation-sort-fields
  base-entity-sort-fields)

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
           :activity.date_time
           :activity.description
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

(def attack-pattern-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          [:name]))

(def malware-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          [:name]))

(def tool-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          [:name]))

(def investigation-sort-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields))
