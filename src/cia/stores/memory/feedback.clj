(ns cia.stores.memory.feedback
  (:require [cia.schemas.feedback :refer [NewFeedback StoredFeedback realize-feedback]]
            [cia.store :refer [IFeedbackStore]]
            [cia.stores.memory.common :as mc]
            [schema.core :as s]))

(s/defn handle-create-feedback :- StoredFeedback
  [state :- (s/atom {s/Str StoredFeedback})
   new-feedback :- NewFeedback
   login :- s/Str
   judgement-id :- s/Str]
  (let [new-id ((mc/random-id "feedback") new-feedback)]
    (get
     (swap! state
            (mc/make-swap-fn realize-feedback)
            new-feedback
            new-id
            login
            judgement-id)
     new-id)))

(mc/def-list-handler handle-list-feedback StoredFeedback)

(defrecord FeedbackStore [state]
  IFeedbackStore
  (create-feedback [_ new-feedback login judgement-id]
    (handle-create-feedback state new-feedback login judgement-id))
  (list-feedback [_ filter-map]
    (handle-list-feedback state filter-map)))
