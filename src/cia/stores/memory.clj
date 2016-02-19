(ns cia.stores.memory
  (:require [cia.schemas.actor :refer [NewActor StoredActor realize-actor]]
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
            [cia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [cia.schemas.verdict :refer [Verdict]]
            [cia.schemas.vocabularies :as v]
            [cia.store :refer :all]
            [clj-time.core :as time]
            [clojure.string :as str]
            [schema.core :as s])
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
      new-model# :- ~NewModel]
     (let [new-id# (~id-fn new-model#)]
       (get
        (swap! state# ~swap-fn new-model# new-id#)
        new-id#))))

(defmacro def-update-handler [name Model NewModel swap-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      id# :- c/ID
      updated-model# :- ~NewModel]
     (get
      (swap! state#
             ~swap-fn
             updated-model#
             id#
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
     filter-map# :- {(s/either s/Keyword [s/Keyword]) s/Any}]
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
  (create-actor [_ new-actor]
    (handle-create-actor state new-actor))
  (update-actor [_ id actor]
    (handle-update-actor state id actor))
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
  (create-campaign [_ new-campaign]
    (handle-create-campaign state new-campaign))
  (update-campaign [_ id new-campaign]
    (handle-update-campaign state id new-campaign))
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
  (create-coa [_ new-coa]
    (handle-create-coa state new-coa))
  (update-coa [_ id new-coa]
    (handle-update-coa state id new-coa))
  (delete-coa [_ id]
    (handle-delete-coa state id))
  (list-coas [_ filter-map]))

;; ExploitTarget

(def-create-handler handle-create-exploit-target
  StoredExploitTarget NewExploitTarget (make-swap-fn realize-exploit-target) (random-id "exploit-target"))

(def-read-handler handle-read-exploit-target StoredExploitTarget)

(def-delete-handler handle-delete-exploit-target StoredExploitTarget)

(defrecord ExplitTargetStore [state]
  IExploitTargetStore
  (read-exploit-target [_ id]
    (handle-read-exploit-target state id))
  (create-exploit-target [_ new-exploit-target]
    (handle-create-exploit-target state new-exploit-target))
  (update-exploit-target [_ exploit-target])
  (delete-exploit-target [_ id]
    (handle-delete-exploit-target state id))
  (list-exploit-targets [_ filter-map]))

;; Feedback

(s/defn handle-create-feedback :- StoredFeedback
  [state :- (s/atom {s/Str StoredFeedback})
   new-feedback :- NewFeedback
   judgement-id :- s/Str]
  (let [new-id ((random-id "feedback") new-feedback)]
    (get
     (swap! state
            (make-swap-fn realize-feedback)
            new-feedback
            new-id
            judgement-id)
     new-id)))

(def-list-handler handle-list-feedback StoredFeedback)

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback judgement-id]
    (handle-create-feedback state new-feedback judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))

;; Incident

(def-create-handler handle-create-incident
  StoredIncident NewIncident (make-swap-fn realize-incident) (random-id "incident"))

(def-read-handler handle-read-incident StoredIncident)

(def-delete-handler handle-delete-incident StoredIncident)

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (handle-read-incident state id))
  (create-incident [_ new-incident]
    (handle-create-incident state new-incident))
  (update-incident [_ incident])
  (delete-incident [_ id]
    (handle-delete-incident state id))
  (list-incidents [_ filter-map]))

;; Indicator

(def-create-handler handle-create-indicator
  StoredIndicator NewIndicator (make-swap-fn realize-indicator) (random-id "indicator"))

(def-read-handler handle-read-indicator StoredIndicator)

(def-delete-handler handle-delete-indicator StoredIndicator)

(def-list-handler handle-list-indicators StoredIndicator)

(s/defn handle-list-indicators-by-observable :- [StoredIndicator]
  [indicator-state :- (s/atom {s/Str StoredIndicator})
   judgement-store :- (s/protocol IJudgementStore)
   observable :- c/Observable]
  (let [judgements (list-judgements judgement-store
                                    {[:observable :type] (:type observable)
                                     [:observable :value] (:value observable)})
        judgement-ids (set (map :id judgements))]
    (filter (fn [indicator]
              (some (fn [judgement-relation]
                      (let [judgement-id (if (map? judgement-relation)
                                          (:judgement judgement-relation)
                                          judgement-relation)]
                        (contains? judgement-ids judgement-id)))
                    (:judgements indicator)))
            (vals @indicator-state))))

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ new-indicator]
    (handle-create-indicator state new-indicator))
  (read-indicator [_ id]
    (handle-read-indicator state id))
  (delete-indicator [_ id]
    (handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (handle-list-indicators state filter-map))
  (list-indicators-by-observable [_ judgement-store observable]
    (handle-list-indicators-by-observable state
                                          judgement-store
                                          observable))
  (list-indicator-sightings-by-observable [_ judgement-store observable]
    (->> (handle-list-indicators-by-observable state
                                               judgement-store
                                               observable)
         (mapcat :sightings))))

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
  (let [[judgement-1 judgement-2 :as judgements] (sort-by (comp :end_time :valid_time) judgements)]
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
   :judgement (:id judgement)
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

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (handle-create-judgement state new-judgement))
  (read-judgement [_ id]
    (handle-read-judgement state id))
  (delete-judgement [_ id]
    (handle-delete-judgement state id))
  (list-judgements [_ filter-map]
    (handle-list-judgements state filter-map))
  (calculate-verdict [_ observable]
    (handle-calculate-verdict state observable)))

;; ttp

(def-create-handler handle-create-ttp
  StoredTTP
  NewTTP
  (make-swap-fn realize-ttp)
  (random-id "ttp"))

(def-read-handler handle-read-ttp StoredTTP)

(def-delete-handler handle-delete-ttp StoredTTP)

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (handle-read-ttp state id))
  (create-ttp [_ new-ttp]
    (handle-create-ttp state new-ttp))
  (update-ttp [_ ttp])
  (delete-ttp [_ id]
    (handle-delete-ttp state id))
  (list-ttps [_ filter-map]))
