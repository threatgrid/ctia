(ns ctia.stores.atom.feedback
  (:require [ctia.schemas.feedback :refer [StoredFeedback]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-feedback (mc/create-handler-from-realized StoredFeedback))
(def handle-list-feedback  (mc/list-handler StoredFeedback))
