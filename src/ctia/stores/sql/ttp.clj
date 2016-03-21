(ns ctia.stores.sql.ttp
  (:import org.joda.time.DateTime
           java.util.UUID)
  (:require
   [schema.core :as s]
   [korma.core :as k]
   [korma.db :as db]
   [clojure.string :as str]
   [clj-time.core :as t]
   [ctia.schemas.common :refer [timestamp]]
   [ctia.schemas.ttp :refer [TTP
                            NewTTP
                            realize-TTP]]
   [ctia.stores.sql.common :refer [create-entity-record
                                  get-entity-record
                                  get-entity-records
                                  delete-entity-record]]))

(k/defentity ttp)
(k/defentity ttp_exploit_targets)
(k/defentity ttp_related_ttps)
(k/defentity ttp_indicators)


(defn- make-id [schema j]
  (str "ttp" "-" (UUID/randomUUID)))


(def ttp-identity-config
  {:entity ttp
   :relations [{:entity ttp_exploit_targets
                :local-id :indicator_id
                :foreign-id :exploit_target_id
                :field :exploit_targets
                :field-foreign-id :exploit_target}

               {:entity indicator_types
                :local-id :indicator_id
                :foreign-id :type_id
                :field :type}

               {:entity indicator_judgements
                :local-id :indicator_id
                :foreign-id :judgement_id
                :field :judgements
                :field-foreign-id :judgement
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_composite_indicator_expression
                :local-id :indicator_id
                :foreign-id :related_indicator_id
                :field :composite_indicator_expression
                :field-foreign-id :indicator
                :metas [:operator
                        :confidence
                        :source
                        :relationship]}

               {:entity indicator_indicated_ttp
                :local-id :indicator_id
                :foreign-id :ttp_id
                :field :indicated_TTP
                :field-foreign-id :ttp
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_suggested_coas
                :local-id :indicator_id
                :foreign-id :coa_id
                :field :suggested_COAs
                :field-foreign-id :coa
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_sightings
                :local-id :indicator_id
                :foreign-id :sighting_id
                :field :sightings
                :field-foreign-id :sighting
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_related_indicators
                :local-id :indicator_id
                :foreign-id :related_indicator_id
                :field :related_indicators
                :field-foreign-id :indicator
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_related_campaigns
                :local-id :indicator_id
                :foreign-id :campaign_id
                :field :related_campaigns
                :field-foreign-id :campaign
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_related_coas
                :local-id :indicator_id
                :foreign-id :coa_id
                :field :related_COAs
                :field-foreign-id :COA
                :metas [:confidence
                        :source
                        :relationship]}

               {:entity indicator_kill_chain_phases
                :local-id :indicator_id
                :foreign-id :kill_chain_phase_id
                :field :kill_chain_phases
                :field-foreign-id :kill_chain_phase}

               {:entity indicator_test_mechanisms
                :local-id :indicator_id
                :foreign-id :test_mechanism_id
                :field :test_mechanisms
                :field-foreign-id :test_mechanism}]})

(s/defn handle-create-indicator [state :- s/Any
                                 new-indicator :- NewIndicator]

  (let [id (make-id Indicator new-indicator)
        realized (realize-indicator new-indicator id)]
    (create-entity-record indicator-identity-config realized)
    (get-entity-record indicator-identity-config id)))

(s/defn handle-update-indicator [state :- s/Any
                                 id :- s/Int
                                 new-indicator :- NewIndicator]

  (let [realized (realize-indicator new-indicator id)]
    (delete-entity-record indicator-identity-config id)
    (create-entity-record indicator-identity-config
                          (merge realized new-indicator))))

(s/defn handle-read-indicator [state :- (s/atom Indicator)
                               id :- String]
  (get-entity-record indicator-identity-config id))

(s/defn handle-delete-indicator [state :- (s/atom Indicator)
                                 id :- String]
  (delete-entity-record indicator-identity-config id))

(s/defn handle-list-indicators [state :- (s/atom Indicator)
                                filter-map :- {}]
  (get-entity-records indicator-identity-config filter-map))

(s/defn handle-list-indicators-sightings
  [state :- (s/atom Indicator)
   filter-map :- {}])
