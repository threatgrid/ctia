(ns cia.stores.es.judgement
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.judgement :refer [Judgement
                                  NewJudgement
                                  realize-judgement]]
   [cia.stores.es.document :refer [create-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "judgement")

(defn- make-id [schema j]
  (str "judgement" "-" (UUID/randomUUID)))

(defn handle-create-judgement [state new-judgement]
  (let [id (make-id Judgement new-judgement)
        realized (realize-judgement new-judgement id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-read-judgement [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-judgement [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-judgements [state filter-map]
  (search-docs es-conn index-name mapping filter-map))

(defn handle-calculate-verdict [state observable])
