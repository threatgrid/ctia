(ns ctia.stores.atom.feedback
  (:require [ctia.schemas.feedback :refer [StoredFeedback]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-feedback (mc/create-handler-from-realized StoredFeedback))
(def handle-read-feedback   (mc/read-handler StoredFeedback))
(def handle-delete-feedback (mc/delete-handler StoredFeedback))
(def handle-list-feedback   (mc/list-handler StoredFeedback))
