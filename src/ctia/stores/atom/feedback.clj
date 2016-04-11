(ns ctia.stores.atom.feedback
  (:require [ctia.schemas.feedback :refer [NewFeedback StoredFeedback realize-feedback]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(s/defn handle-create-feedback :- StoredFeedback
  [state :- (s/atom {s/Str StoredFeedback})
   new-feedback :- StoredFeedback
   login :- s/Str
   judgement-id :- s/Str]
  (let [id (:id new-feedback)]
    (get (swap! state assoc id new-feedback) id)))

(mc/def-list-handler handle-list-feedback StoredFeedback)
