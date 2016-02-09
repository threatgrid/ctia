(ns cia.stores.memory
  (:require [cia.schemas.feedback :refer [Feedback NewFeedback]]
            [cia.schemas.judgement :refer [Judgement NewJudgement]]
            [cia.store :refer :all]
            [schema.core :as s])
  (:import org.joda.time.DateTime
           java.util.UUID))

;; Judgement

(defn swap-create-judgement [judgements judgement id]
  (assoc judgements id (assoc judgement
                              :id (str id)
                              :priority 100
                              :timestamp (DateTime.)
                              :severity 100
                              :confidence "Low")))

(s/defn handle-create-judgement :- Judgement
  [state :- (s/atom {s/Str Judgement})
   new-judgement :- NewJudgement]
  (let [new-id (str "judgement-" (UUID/randomUUID))]
    (get
     (swap! state swap-create-judgement new-judgement new-id)
     new-id)))

(s/defn handle-read-judgement :- (s/maybe Judgement)
  [state :- (s/atom {s/Str Judgement})
   judgement-id :- s/Str]
  (get @state judgement-id))

(s/defn handle-delete-judgement :- s/Bool
  [state :- (s/atom {s/Str Judgement})
   judgement-id :- s/Str]
  (if (contains? @state judgement-id)
    (do (swap! state dissoc judgement-id)
        true)
    false))

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

;; Feedback

(defn swap-create-feedback [feedbacks feedback id judgement-id]
  (assoc feedbacks id (assoc feedback
                             :id id
                             :judgement judgement-id)))

(s/defn handle-create-feedback :- Feedback
  [state :- (s/atom {s/Str Feedback})
   new-feedback :- NewFeedback
   judgement-id :- s/Str]
  (let [new-id (str "feedback-" (UUID/randomUUID))]
    (get
     (swap! state swap-create-feedback new-feedback new-id judgement-id)
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
