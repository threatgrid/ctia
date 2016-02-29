(ns cia.stores.es.judgement
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [clj-time.core :as t]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.common :refer [disposition-map]]
   [cia.schemas.judgement :refer [Judgement
                                  NewJudgement
                                  realize-judgement]]
   [cia.stores.es.document :refer [create-doc
                                   get-doc
                                   delete-doc
                                   search-docs
                                   raw-search-docs
                                   ]]))

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

(defn list-unexpired-judgements-by-observable
  [{:keys [value type]}]

  (let [sort {:priority "desc"
              :disposition "asc"
              "valid_time.start_time"
              {:order "asc"
               :mode "min"
               :nested_filter
               {"range" {"valid_time.start_time" {"lt" "now/d"}}}}}
        observable-filter
        {:nested {:path "observable"
                  :query
                  {:bool
                   {:must [{:term {"observable.type" type}}
                           {:term {"observable.value" value}}]}}}}
        time-filter
        {:nested {:path "valid_time"
                  :query
                  {:bool
                   {:must [{:range
                            {"valid_time.start_time" {"lt" "now/d"}}}
                           {:range
                            {"valid_time.end_time" {"gt" "now/d"}}}]}}}}
        query {:filtered
               {:query observable-filter
                :filter
                {:bool
                 {:must time-filter}}}}]

    (raw-search-docs
     es-conn
     index-name
     mapping
     query
     sort)))

(defn- make-verdict [judgement]
  {:disposition (:disposition judgement)
   :judgement (:id judgement)
   :disposition_name (get disposition-map (:disposition judgement))})

(defn handle-calculate-verdict [state observable]
  (-> (list-unexpired-judgements-by-observable observable)
      first
      make-verdict))
