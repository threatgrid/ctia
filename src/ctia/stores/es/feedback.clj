(ns ctia.stores.es.feedback
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.feedback :refer [Feedback
                                  NewFeedback
                                  StoredFeedback
                                  realize-feedback]]
   [ctia.stores.es.document :refer [create-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "feedback")

(def coerce-stored-feedback
  (c/coercer! (s/maybe StoredFeedback)
              sc/json-schema-coercion-matcher))

(defn handle-create-feedback [state realized-new-feedback _ _]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-feedback)
      coerce-stored-feedback))

(defn handle-delete-feedback [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-feedback [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-feedback)))
