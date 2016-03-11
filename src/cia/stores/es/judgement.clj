(ns cia.stores.es.judgement
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [clj-time.core :as t]
   [cia.schemas.common :refer [disposition-map]]
   [cia.schemas.judgement :refer [Judgement
                                  NewJudgement
                                  realize-judgement]]
   [cia.stores.es.query :refer
    [unexpired-judgements-by-observable-query]]

   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs
                                   raw-search-docs]]))

(def ^{:private true} mapping "judgement")

(defn- make-id [schema j]
  (str "judgement" "-" (UUID/randomUUID)))

(defn handle-create-judgement [state login new-judgement]
  (let [id (make-id Judgement new-judgement)
        realized (realize-judgement new-judgement id login)]

    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-read-judgement [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-add-indicator-to-judgement
  "add an indicator relation to a judgement"
  [state judgement-id indicator-rel]

  (let [judgement (handle-read-judgement state judgement-id)
        indicator-rels (:indicators judgement)
        updated-rels (conj indicator-rels indicator-rel)
        updated {:indicators (set updated-rels)}]

    (update-doc (:conn state)
                (:index state)
                mapping
                judgement-id
                updated)

    indicator-rel))

(defn handle-delete-judgement [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-judgements [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))

(defn list-unexpired-judgements-by-observable
  [state observable]

  (let [sort {:priority "desc"
              :disposition "asc"
              "valid_time.start_time"
              {:order "asc"
               :mode "min"
               :nested_filter
               {"range" {"valid_time.start_time" {"lt" "now/d"}}}}}
        query
        (unexpired-judgements-by-observable-query observable)]

    (raw-search-docs (:conn state)
                     (:index state)
                     mapping
                     query
                     sort)))

(defn- make-verdict [judgement]
  {:disposition (:disposition judgement)
   :judgement (:id judgement)
   :disposition_name (get disposition-map (:disposition judgement))})

(defn handle-calculate-verdict [state observable]
  (-> (list-unexpired-judgements-by-observable state observable)
      first
      make-verdict))
