(ns cia.stores.memory
  (:require [cia.schemas.actor :refer [Actor NewActor realize-actor]]
            [cia.schemas.campaign :refer [Campaign NewCampaign realize-campaign]]
            [cia.schemas.coa :refer [COA NewCOA realize-coa]]
            [cia.schemas.feedback :refer [Feedback NewFeedback realize-feedback]]
            [cia.schemas.judgement :refer [Judgement NewJudgement realize-judgement]]
            [cia.store :refer :all]
            [clojure.string :as str]
            [schema.core :as s])
  (:import org.joda.time.DateTime
           java.util.UUID))

(defn make-id [schema]
  (str (str/lower-case (s/schema-name schema)) "-" (UUID/randomUUID)))

(defmacro def-read-handler [name Model]
  `(s/defn ~name :- (s/maybe ~Model)
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (get (deref state#) id#)))

(defmacro def-create-handler [name Model NewModel swap-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      new-model# :- ~NewModel]
     (let [new-id# (make-id ~Model)]
       (get
        (swap! state# ~swap-fn new-model# new-id#)
        new-id#))))

(defmacro def-delete-handler [name Model]
  `(s/defn ~name :- s/Bool
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (if (contains? (deref state#) id#)
       (do (swap! state# dissoc id#)
           true)
       false)))

(defn make-swap-fn [realize-fn]
  (fn [state-map & [new-model id :as args]]
    (assoc state-map id (apply realize-fn args))))

;; Actor

(def-create-handler handle-create-actor
  Actor NewActor (make-swap-fn realize-actor))

(def-read-handler handle-read-actor Actor)

(def-delete-handler handle-delete-actor Actor)

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (handle-read-actor state id))
  (create-actor [_ new-actor]
    (handle-create-actor state new-actor))
  (update-actor [_ actor])
  (delete-actor [_ id]
    (handle-delete-actor state id))
  (list-actors [_ filter-map]))

;; Campaign

(def-create-handler handle-create-campaign
  Campaign NewCampaign (make-swap-fn realize-campaign))

(def-read-handler handle-read-campaign Campaign)

(def-delete-handler handle-delete-campaign Campaign)

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (handle-read-campaign state id))
  (create-campaign [_ new-campaign]
    (handle-create-campaign state new-campaign))
  (update-campaign [_ campaign])
  (delete-campaign [_ id]
    (handle-delete-campaign state id))
  (list-campaigns [_ filter-map]))

;; COA

(def-create-handler handle-create-coa
  COA NewCOA (make-swap-fn realize-coa))

(def-read-handler handle-read-coa COA)

(def-delete-handler handle-delete-coa COA)

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (handle-read-coa state id))
  (create-coa [_ new-coa]
    (handle-create-coa state new-coa))
  (update-coa [_ coa])
  (delete-coa [_ id]
    (handle-delete-coa state id))
  (list-coas [_ filter-map]))

;; Feedback

(s/defn handle-create-feedback :- Feedback
  [state :- (s/atom {s/Str Feedback})
   new-feedback :- NewFeedback
   judgement-id :- s/Str]
  (let [new-id (str "feedback-" (UUID/randomUUID))]
    (get
     (swap! state
            (make-swap-fn realize-feedback)
            new-feedback
            new-id
            judgement-id)
     new-id)))

(s/defn handle-list-feedback :- (s/maybe [Feedback])
  [state :- (s/atom {s/Str Feedback})
   filter-map :- {s/Keyword s/Any}]
  (filter (fn [feedback]
            (every? (fn [[k v]]
                      (= v (get feedback k ::not-found)))
                    filter-map))
          (vals @state)))

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback judgement-id]
    (handle-create-feedback state new-feedback judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))

;; Judgement

(def-create-handler handle-create-judgement
  Judgement NewJudgement (make-swap-fn realize-judgement))

(def-read-handler handle-read-judgement Judgement)

(def-delete-handler handle-delete-judgement Judgement)

(defrecord JudgementStore [state]
  IJudgementStore
  (create-judgement [_ new-judgement]
    (handle-create-judgement state new-judgement))
  (read-judgement [_ id]
    (handle-read-judgement state id))
  (delete-judgement [_ id]
    (handle-delete-judgement state id))
  (list-judgements-by-observable [this observable])
  (list-judgements-by-indicator [this indicator-id])
  (calculate-verdict [this observable]))
