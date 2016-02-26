(ns cia.stores.es.feedback
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.feedback :refer [Feedback
                                 NewFeedback
                                 realize-feedback]]
   [cia.stores.es.document :refer [create-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "feedback")

(defn- make-id [schema j]
  (str "feedback" "-" (UUID/randomUUID)))

(defn handle-create-feedback [state new-feedback judgement-id]
  (let [id (make-id Feedback new-feedback)
        realized (realize-feedback new-feedback id judgement-id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-delete-feedback [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-feedback [state filter-map]
  (search-docs es-conn index-name mapping filter-map))
