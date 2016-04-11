(ns ctia.stores.es.feedback
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.feedback :refer [Feedback
                                 NewFeedback
                                 realize-feedback]]
   [ctia.stores.es.document :refer [create-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "feedback")

(defn handle-create-feedback [state realized-new-feedback _ _]
  (create-doc (:conn state)
              (:index state)
              mapping
              realized-new-feedback))

(defn handle-delete-feedback [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-feedback [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))
