(ns cia.stores.es.feedback
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.schemas.feedback :refer [Feedback
                                 NewFeedback
                                 realize-feedback]]
   [cia.stores.es.document :refer [create-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "feedback")

(defn- make-id [schema j]
  (str "feedback" "-" (UUID/randomUUID)))

(defn handle-create-feedback [state new-feedback login judgement-id]
  (let [id (make-id Feedback new-feedback)
        realized (realize-feedback new-feedback id login judgement-id)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

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
