(ns ctia.entity.judgement.es-store
  (:require [clj-momo.lib.es.document :refer [search-docs]]
            [clj-momo.lib.time :as time]
            [ctia.domain.access-control :refer [allow-write?]]
            [ctia.entity.judgement.schemas
             :refer
             [PartialStoredJudgement StoredJudgement]]
            [ctia.properties :as p]
            [ctia.schemas.core :refer [Verdict]]
            [ctia.store :refer [IJudgementStore IQueryStringSearchableStore IStore]]
            [ctia.stores.es
             [crud :as crud]
             [mapping :as em]
             [query :refer [active-judgements-by-observable-query find-restriction-query-part]]
             [schemas :refer [ESConnState]]]
            [ctim.schemas.common :refer [disposition-map]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]))

(def judgement-mapping "judgement")

(def judgement-mapping-def
  {"judgement"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:observable em/observable
      :disposition em/long-type
      :disposition_name em/token
      :priority em/long-type
      :confidence em/token
      :severity em/token
      :valid_time em/valid-time
      :reason em/sortable-all-text
      :reason_uri em/token})}})

(def coerce-stored-judgement-list
  (c/coercer! [(s/maybe StoredJudgement)]
              sc/json-schema-coercion-matcher))

(def handle-create (crud/handle-create :judgement StoredJudgement))
(def handle-update (crud/handle-update :judgement StoredJudgement))
(def handle-read (crud/handle-read :judgement PartialStoredJudgement))
(def handle-delete (crud/handle-delete :judgement PartialStoredJudgement))
(def handle-list (crud/handle-find :judgement PartialStoredJudgement))
(def handle-query-string-search (crud/handle-query-string-search :judgement PartialStoredJudgement))
(def handle-query-string-count (crud/handle-query-string-count :judgement))
(def handle-aggregate (crud/handle-aggregate :judgement))

(defn list-active-by-observable
  [state observable ident get-in-config]
  (let [now-str (time/format-date-time (time/timestamp))
        composed-query
        (assoc-in
         (find-restriction-query-part ident get-in-config)
         [:bool :must]
         (active-judgements-by-observable-query
          observable
          now-str))
        params
        {:sort
         {:priority
          "desc"

          :disposition
          "asc"

          "valid_time.start_time"
          {:order "asc"
           :mode "min"
           :nested_filter
           {"range" {"valid_time.start_time" {"lte" now-str}}}}}}]
    (some->>
     (search-docs (:conn state)
                  (:index state)
                  judgement-mapping
                  composed-query
                  nil
                  params)
     :data
     coerce-stored-judgement-list)))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :disposition_name (get disposition-map (:disposition judgement))
   :judgement_id (:id judgement)
   :observable (:observable judgement)
   :valid_time (:valid_time judgement)})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [{{{:keys [get-in-config]} :ConfigService} :services
    :as state} :- ESConnState
    observable
    ident]
  (some-> (list-active-by-observable state observable ident get-in-config)
          first
          make-verdict))

(defrecord JudgementStore [state]
  IStore
  (create-record [_ new-judgements ident params]
    (handle-create state new-judgements ident params))
  (read-record [_ id ident params]
    (handle-read state id ident params))
  (delete-record [_ id ident params]
    (handle-delete state id ident params))
  (update-record [_ id judgement ident params]
    (handle-update state id judgement ident params))
  (list-records [_ filter-map ident params]
    (handle-list state filter-map ident params))

  IJudgementStore
  (list-judgements-by-observable [this observable ident params]
    (handle-list state {:all-of {[:observable :type]  (:type observable)
                                 [:observable :value] (:value observable)}} ident params))
  (calculate-verdict [_ observable ident]
    (handle-calculate-verdict state observable ident))

  IQueryStringSearchableStore
  (query-string-search [_ search-query ident params]
    (handle-query-string-search state search-query ident params))
  (query-string-count [_ search-query ident]
    (handle-query-string-count state search-query ident))
  (aggregate [_ search-query agg-query ident]
    (handle-aggregate state search-query agg-query ident)))
