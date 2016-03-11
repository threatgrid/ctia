(ns cia.stores.memory
  (:require [cia.auth :as auth]
            [cia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [cia.schemas.identity :as identity]
            [cia.schemas.campaign
             :refer [NewCampaign StoredCampaign realize-campaign]]
            [cia.schemas.coa :refer [NewCOA StoredCOA realize-coa]]
            [cia.schemas.common :as c]
            [cia.schemas.exploit-target
             :refer [NewExploitTarget StoredExploitTarget realize-exploit-target]]
            [cia.schemas.feedback :refer [NewFeedback StoredFeedback realize-feedback]]
            [cia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [cia.schemas.indicator
             :refer [NewIndicator StoredIndicator realize-indicator]]
            [cia.schemas.judgement
             :refer [NewJudgement StoredJudgement realize-judgement]]
            [cia.schemas.sighting
             :refer [NewSighting StoredSighting realize-sighting]]
            [cia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [cia.schemas.verdict :refer [Verdict]]
            [cia.schemas.vocabularies :as v]
            [cia.store :refer :all]
            [clj-time.core :as time]
            [clojure.string :as str]
            [schema.core :as s]
            [cia.schemas.relationships :as rel])
  (:import java.util.UUID))

(defn random-id [prefix]
  (fn [_new-entity_]
    (str prefix "-" (UUID/randomUUID))))

(defmacro def-read-handler [name Model]
  `(s/defn ~name :- (s/maybe ~Model)
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (get (deref state#) id#)))

(defmacro def-create-handler [name Model NewModel swap-fn id-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      login# :- s/Str
      new-model# :- ~NewModel]
     (let [new-id# (~id-fn new-model#)]
       (get
        (swap! state# ~swap-fn new-model# new-id# login#)
        new-id#))))

(defmacro def-update-handler [name Model NewModel swap-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      id# :- c/ID
      login# :- s/Str
      updated-model# :- ~NewModel]
     (get
      (swap! state#
             ~swap-fn
             updated-model#
             id#
             login#
             (get (deref state#) id#))
      id#)))

(defmacro def-delete-handler [name Model]
  `(s/defn ~name :- s/Bool
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (if (contains? (deref state#) id#)
       (do (swap! state# dissoc id#)
           true)
       false)))

(defmacro def-list-handler [name Model]
  `(s/defn ~name :- (s/maybe [~Model])
     [state# :- (s/atom {s/Str ~Model})
      filter-map# :- {s/Any s/Any}]
     (into []
           (filter (fn [model#]
                     (every? (fn [[k# v#]]
                               (if (sequential? k#)
                                 (= v# (get-in model# k# ::not-found))
                                 (= v# (get model# k# ::not-found))))
                             filter-map#))
                   (vals (deref state#))))))

(defn make-swap-fn [entity-fn]
  (fn [state-map & [new-model id :as args]]
    (assoc state-map id (apply entity-fn args))))

;; Actor

(def swap-actor (make-swap-fn realize-actor))

(def-create-handler handle-create-actor
  StoredActor NewActor swap-actor (random-id "actor"))

(def-read-handler handle-read-actor StoredActor)

(def-delete-handler handle-delete-actor StoredActor)

(def-update-handler handle-update-actor
  StoredActor NewActor swap-actor)

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (handle-read-actor state id))
  (create-actor [_ login new-actor]
    (handle-create-actor state login new-actor))
  (update-actor [_ id login actor]
    (handle-update-actor state id login actor))
  (delete-actor [_ id]
    (handle-delete-actor state id))
  (list-actors [_ filter-map]))

;; Campaign

(def swap-campaign (make-swap-fn realize-campaign))

(def-create-handler handle-create-campaign
  StoredCampaign NewCampaign swap-campaign (random-id "campaign"))

(def-read-handler handle-read-campaign StoredCampaign)

(def-delete-handler handle-delete-campaign StoredCampaign)

(def-update-handler handle-update-campaign
  StoredCampaign NewCampaign swap-campaign)

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (handle-read-campaign state id))
  (create-campaign [_ login new-campaign]
    (handle-create-campaign state login new-campaign))
  (update-campaign [_ id login new-campaign]
    (handle-update-campaign state id login new-campaign))
  (delete-campaign [_ id]
    (handle-delete-campaign state id))
  (list-campaigns [_ filter-map]))

;; COA

(def swap-coa (make-swap-fn realize-coa))

(def-create-handler handle-create-coa
  StoredCOA NewCOA swap-coa (random-id "coa"))

(def-read-handler handle-read-coa StoredCOA)

(def-delete-handler handle-delete-coa StoredCOA)

(def-update-handler handle-update-coa
  StoredCOA NewCOA swap-coa)

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (handle-read-coa state id))
  (create-coa [_ login new-coa]
    (handle-create-coa state login new-coa))
  (update-coa [_ id login new-coa]
    (handle-update-coa state id login new-coa))
  (delete-coa [_ id]
    (handle-delete-coa state id))
  (list-coas [_ filter-map]))

;; ExploitTarget

(def swap-exploit-target (make-swap-fn realize-exploit-target))

(def-create-handler handle-create-exploit-target
  StoredExploitTarget NewExploitTarget swap-exploit-target (random-id "exploit-target"))

(def-read-handler handle-read-exploit-target StoredExploitTarget)

(def-delete-handler handle-delete-exploit-target StoredExploitTarget)

(def-update-handler handle-update-exploit-target
  StoredExploitTarget NewExploitTarget swap-exploit-target)

(defrecord ExploitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (handle-read-exploit-target state id))
  (create-exploit-target [_ login new-exploit-target]
    (handle-create-exploit-target state login new-exploit-target))
  (update-exploit-target [_ id login new-exploit-target]
    (handle-update-exploit-target state id login new-exploit-target))
  (delete-exploit-target [_ id]
    (handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]))

;; Feedback

(s/defn handle-create-feedback :- StoredFeedback
  [state :- (s/atom {s/Str StoredFeedback})
   new-feedback :- NewFeedback
   login :- s/Str
   judgement-id :- s/Str]
  (let [new-id ((random-id "feedback") new-feedback)]
    (get
     (swap! state
            (make-swap-fn realize-feedback)
            new-feedback
            new-id
            login
            judgement-id)
     new-id)))

(def-list-handler handle-list-feedback StoredFeedback)

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback login judgement-id]
    (handle-create-feedback state new-feedback login judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))

;; Incident

(def swap-incident (make-swap-fn realize-incident))

(def-create-handler handle-create-incident
  StoredIncident NewIncident swap-incident (random-id "incident"))

(def-read-handler handle-read-incident StoredIncident)

(def-delete-handler handle-delete-incident StoredIncident)

(def-update-handler handle-update-incident
  StoredIncident NewIncident swap-incident)

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (handle-read-incident state id))
  (create-incident [_ login new-incident]
    (handle-create-incident state login new-incident))
  (update-incident [_ id login incident]
    (handle-update-incident state id login incident))
  (delete-incident [_ id]
    (handle-delete-incident state id))
  (list-incidents [_ filter-map]))

;; Indicator

(def swap-indicator (make-swap-fn realize-indicator))

(def-create-handler handle-create-indicator
  StoredIndicator NewIndicator swap-indicator (random-id "indicator"))

(def-read-handler handle-read-indicator StoredIndicator)

(def-delete-handler handle-delete-indicator StoredIndicator)

(def-update-handler handle-update-indicator
  StoredIndicator NewIndicator swap-indicator)

(def-list-handler handle-list-indicators StoredIndicator)

(s/defn handle-list-indicators-by-judgements :- (s/maybe [StoredIndicator])
  [indicator-state :- (s/atom {s/Str StoredIndicator})
   judgements :- [StoredJudgement]]
  (let [judgement-ids (set (map :id judgements))]
    ;; Note (polygloton, 2016-03-10):
    ;; Find indicators using the :judgements relationship on the indicators.
    ;; It could be done the other way around, since judgements have :indicators
    ;; relationships.  Not sure which is more correct.
    (filter (fn [indicator]
              (some (fn [judgement-relationship]
                      (let [judgement-id (:judgement_id judgement-relationship)]
                        (contains? judgement-ids judgement-id)))
                    (:judgements indicator)))
            (vals @indicator-state))))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ login new-indicator]
    (handle-create-indicator state login new-indicator))
  (update-indicator [_ id login new-indicator]
    (handle-update-indicator state id login new-indicator))
  (read-indicator [_ id]
    (handle-read-indicator state id))
  (delete-indicator [_ id]
    (handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (handle-list-indicators state filter-map))
  (list-indicators-by-judgements [_ judgements]
    (handle-list-indicators-by-judgements state judgements)))

;; Sighting

(def swap-sighting (make-swap-fn realize-sighting))

(def-create-handler handle-create-sighting
  StoredSighting NewSighting swap-sighting (random-id "sighting"))

(def-update-handler handle-update-sighting
  StoredSighting NewSighting swap-sighting)

(def-read-handler handle-read-sighting StoredSighting)

(def-delete-handler handle-delete-sighting StoredSighting)

(def-list-handler handle-list-sightings StoredSighting)

(s/defn handle-list-sightings-by-indicators :- (s/maybe [StoredSighting])
  [sightings-state :- (s/atom {s/Str StoredSighting})
   indicators :- (s/maybe [StoredIndicator])]
  ;; Find sightings using the :sightings relationship on indicators
  (let [sightings-map @sightings-state]
    (->> indicators
         (map :sightings)
         flatten
         (map :sighting_id)
         (remove nil?)
         (map (fn [s-id]
                (get sightings-map s-id)))
         (remove nil?))))

(defrecord SightingStore [state]
  ISightingStore
  (read-sighting [_ id]
    (handle-read-sighting state id))
  (create-sighting [_ login new-sighting]
    (handle-create-sighting state login new-sighting))
  (update-sighting [_ id login sighting]
    (handle-update-sighting state id login sighting))
  (delete-sighting [_ id]
    (handle-delete-sighting state id))
  (list-sightings [_ filter-map]
    (handle-list-sightings state filter-map))
  (list-sightings-by-indicators [_ indicators]
    (handle-list-sightings-by-indicators state indicators)))

;; Judgement

(def-create-handler handle-create-judgement
  StoredJudgement NewJudgement (make-swap-fn realize-judgement) (random-id "judgement"))

(def-read-handler handle-read-judgement StoredJudgement)

(def-delete-handler handle-delete-judgement StoredJudgement)

(def-list-handler handle-list-judgements StoredJudgement)

(defn judgement-expired? [judgement now]
  (if-let [expires (get-in judgement [:valid_time :end_time])]
    (time/after? now expires)
    false))

(defn higest-priority [& judgements]
  ;; pre-sort for deterministic tie breaking
  (let [[judgement-1 judgement-2 :as judgements] (sort-by (comp :start_time :valid_time) judgements)]
    (cond
      (some nil? judgements)
      (first (remove nil? judgements))

      (not= (:priority judgement-1) (:priority judgement-2))
      (last (sort-by :priority judgements))

      :else (loop [[d-num & rest-d-nums] (sort (keys c/disposition-map))]
              (cond
                (nil? d-num) nil
                (= d-num (:disposition judgement-1)) judgement-1
                (= d-num (:disposition judgement-2)) judgement-2
                :else (recur rest-d-nums))))))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:disposition (:disposition judgement)
   :judgement_id (:id judgement)
   :disposition_name (get c/disposition-map (:disposition judgement))})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [state :- (s/atom {s/Str StoredJudgement})
   observable :- c/Observable]
  (if-let [judgement
           (let [now (time/now)]
             (loop [[judgement & more-judgements] (vals @state)
                    result nil]
               (cond
                 (nil? judgement)
                 result

                 (not= observable (:observable judgement))
                 (recur more-judgements result)

                 (judgement-expired? judgement now)
                 (recur more-judgements result)

                 :else
                 (recur more-judgements (higest-priority judgement result)))))]
    (make-verdict judgement)))

(s/defn handle-add-indicator-to-judgement :- (s/maybe rel/RelatedIndicator)
  [state :- (s/atom {s/Str StoredJudgement})
   judgement-id :- s/Str
   indicator-rel :- rel/RelatedIndicator]
  ;; Possible concurrency issue, maybe state should be a ref?
  (when (contains? @state judgement-id)
    (swap! state update-in [judgement-id :indicators] conj indicator-rel)
    indicator-rel))

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ login new-judgement]
    (handle-create-judgement state login new-judgement))
  (read-judgement [_ id]
    (handle-read-judgement state id))
  (delete-judgement [_ id]
    (handle-delete-judgement state id))
  (list-judgements [_ filter-map]
    (handle-list-judgements state filter-map))
  (calculate-verdict [_ observable]
    (handle-calculate-verdict state observable))
  (list-judgements-by-observable [this observable]
    (list-judgements this {[:observable :type]  (:type observable)
                           [:observable :value] (:value observable)}))
  (add-indicator-to-judgement [_ judgement-id indicator-rel]
    (handle-add-indicator-to-judgement state judgement-id indicator-rel)))

;; ttp

(def swap-ttp (make-swap-fn realize-ttp))

(def-create-handler handle-create-ttp
  StoredTTP NewTTP swap-ttp (random-id "ttp"))

(def-read-handler handle-read-ttp StoredTTP)

(def-delete-handler handle-delete-ttp StoredTTP)

(def-update-handler handle-update-ttp
  StoredTTP NewTTP swap-ttp)

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (handle-read-ttp state id))
  (create-ttp [_ login new-ttp]
    (handle-create-ttp state login new-ttp))
  (update-ttp [_ id login new-ttp]
    (handle-update-ttp state id login new-ttp))
  (delete-ttp [_ id]
    (handle-delete-ttp state id))
  (list-ttps [_ filter-map]))

;; identity

(s/defn handle-create-identity :- identity/Identity
  [state :- (s/atom {identity/Login identity/Identity})
   new-identity :- identity/Identity]
  (let [id (:login new-identity)]
    (get
     (swap! state assoc id new-identity)
     id)))

(s/defn handle-read-identity :- (s/maybe identity/Identity)
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (get @state login))

(s/defn handle-delete-identity :- s/Bool
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (if (contains? @state login)
    (do (swap! state dissoc login)
        true)
    false))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (handle-read-identity state login))
  (create-identity [_ new-identity]
    (handle-create-identity state new-identity))
  (delete-identity [_ org-id role]
    (handle-delete-identity state org-id role)))
