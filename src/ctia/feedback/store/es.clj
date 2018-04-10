(ns ctia.feedback.store.es
  (:require
   [ctia.store :refer [IStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core :refer [StoredFeedback PartialStoredFeedback]]))

(def handle-create (crud/handle-create :feedback StoredFeedback))
(def handle-read (crud/handle-read :feedback PartialStoredFeedback))
(def handle-update (crud/handle-update :feedback StoredFeedback))
(def handle-delete (crud/handle-delete :feedback StoredFeedback))
(def handle-list (crud/handle-find :feedback PartialStoredFeedback))

(defrecord FeedbackStore [state]
  IStore
  (create [_ new-feedbacks ident params]
    (handle-create state new-feedbacks ident params))
  (read [_ id ident params]
    (handle-read state id ident params))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params)))
