(ns ctia.stores.es.feedback
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredFeedback PartialStoredFeedback]]))

(def handle-create (crud/handle-create :feedback StoredFeedback))
(def handle-read (crud/handle-read :feedback PartialStoredFeedback))
(def handle-update (crud/handle-update :feedback StoredFeedback))
(def handle-delete (crud/handle-delete :feedback StoredFeedback))
(def handle-list (crud/handle-find :feedback PartialStoredFeedback))
