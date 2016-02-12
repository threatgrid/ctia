(ns cia.stores.memory
  (:require [cia.schemas.actor :refer [Actor NewActor realize-actor]]
            [cia.schemas.campaign :refer [Campaign NewCampaign realize-campaign]]
            [cia.schemas.coa :refer [COA NewCOA realize-coa]]
            [cia.schemas.common :as c]
            [cia.schemas.exploit-target
             :refer [ExploitTarget NewExploitTarget realize-exploit-target]]
            [cia.schemas.feedback :refer [Feedback NewFeedback realize-feedback]]
            [cia.schemas.incident :refer [Incident NewIncident realize-incident]]
            [cia.schemas.indicator
             :refer [Indicator NewIndicator realize-indicator]]
            [cia.schemas.judgement
             :refer [Judgement NewJudgement realize-judgement]]
            [cia.schemas.ttp :refer [NewTTP TTP realize-ttp]]
            [cia.schemas.verdict :refer [Verdict]]
            [cia.schemas.vocabularies :as v]
            [cia.store :refer :all]
            [clj-time.core :as time]
            [clojure.string :as str]
            [schema.core :as s])
  (:import java.util.UUID))

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

;; ExploitTarget

(def-create-handler handle-create-exploit-target
  ExploitTarget NewExploitTarget (make-swap-fn realize-exploit-target))

(def-read-handler handle-read-exploit-target ExploitTarget)

(def-delete-handler handle-delete-exploit-target ExploitTarget)

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

(def-list-handler handle-list-feedback Feedback)

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback judgement-id]
    (handle-create-feedback state new-feedback judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))

;; Incident

(def-create-handler handle-create-incident
  Incident NewIncident (make-swap-fn realize-incident))

(def-read-handler handle-read-incident Incident)

(def-delete-handler handle-delete-incident Incident)

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
  Indicator NewIndicator (make-swap-fn realize-indicator))

(def-read-handler handle-read-indicator Indicator)

(def-delete-handler handle-delete-indicator Indicator)

(def-list-handler handle-list-indicators Indicator)

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
  (list-indicator-sightings [_ filter-map]
    (->> (handle-list-indicators state filter-map)
         (mapcat :sightings))))

;; Judgement

(def-create-handler handle-create-judgement
  Judgement NewJudgement (make-swap-fn realize-judgement))

(def-read-handler handle-read-judgement Judgement)

(def-delete-handler handle-delete-judgement Judgement)

(def-list-handler handle-list-judgements Judgement)

(defn judgement-expired? [judgement now]
  (if-let [expires (:expires judgement)]
    (time/after? now expires)
    false))

(defn higest-priority [& judgements]
  ;; pre-sort for deterministic tie breaking
  (let [[judgement-1 judgement-2 :as judgements] (sort-by :timestamp judgements)]
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
  [judgement :- Judgement]
  {:disposition (:disposition judgement)
   :judgement (:id judgement)
   :disposition_name (get c/disposition-map (:disposition judgement))})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [state :- (s/atom {s/Str Judgement})
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

(def-create-handler handle-create-ttp TTP NewTTP (make-swap-fn realize-ttp))

(def-read-handler handle-read-ttp TTP)

(def-delete-handler handle-delete-ttp TTP)

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
