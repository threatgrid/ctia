(ns ctia.stores.es.feedback
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.feedback :refer [StoredFeedback]]))

(def handle-create-feedback (crud/handle-create :feedback StoredFeedback))
(def handle-read-feedback (crud/handle-read :feedback StoredFeedback))
(def handle-update-feedback (crud/handle-update :feedback StoredFeedback))
(def handle-delete-feedback (crud/handle-delete :feedback StoredFeedback))
(def handle-list-feedback (crud/handle-find :feedback StoredFeedback))
