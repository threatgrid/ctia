(ns ctia.feedback.store.es
  (:require [ctia.schemas.core :refer [PartialStoredFeedback StoredFeedback]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store FeedbackStore :feedback StoredFeedback PartialStoredFeedback)
