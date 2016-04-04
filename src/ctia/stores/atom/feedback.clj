(ns ctia.stores.atom.feedback
  (:require [ctia.schemas.feedback :refer [NewFeedback StoredFeedback realize-feedback]]
            [ctia.stores.atom.common :as mc]
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
