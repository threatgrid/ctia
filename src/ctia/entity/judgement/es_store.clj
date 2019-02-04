(ns ctia.entity.judgement.es-store
  (:require [clj-momo.lib.es.document :refer [search-docs update-doc]]
            [clj-momo.lib.time :as time]
            [ctia.store :refer :all]
            [ctia.domain.access-control :refer [allow-write?]]
            [ctia.entity.judgement.schemas
             :refer
             [PartialStoredJudgement StoredJudgement]]
            [ctia.schemas.core :refer [Verdict]]
            [ctia.store :refer [IJudgementStore IQueryStringSearchableStore IStore]]
            [ctia.stores.es
             [crud :as crud]
             [mapping :as em]
             [query :refer [active-judgements-by-observable-query find-restriction-query-part]]]
            [ctim.schemas.common :refer [disposition-map]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]))

(def judgement-mapping "judgement")

(def judgement-mapping-def
  {"judgement"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:observable em/observable
      :disposition {:type "long"}
      :disposition_name em/token
      :priority {:type "long"}
      :confidence em/token
      :severity em/token
      :valid_time em/valid-time
      :reason em/all_text
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

(defn handle-add-indicator-to
  "add an indicator relation to a judgement"
  [state judgement-id indicator-rel ident]
  (let [judgement (handle-read state judgement-id ident {})
        indicator-rels (:indicators judgement)
        updated-rels (conj indicator-rels indicator-rel)
        updated {:indicators (set updated-rels)}]
    (if (allow-write? judgement ident)
      (do (update-doc (:conn state)
                      (:index state)
                      judgement-mapping
                      judgement-id
                      updated
                      (get-in state [:props :refresh] false))
          indicator-rel)
      (throw (ex-info "You are not allowed to update this document"
                      {:type :access-control-error})))))


(defn list-active-by-observable
  [state observable ident]
  (let [now-str (time/format-date-time (time/timestamp))
        composed-query
        (assoc-in
         (find-restriction-query-part ident)
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
  [state observable ident]

  (some-> (list-active-by-observable state observable ident)
          first
          make-verdict))

(defrecord JudgementStore [state]
  IStore
  (create-record [_ new-judgements ident params]
    (handle-create state new-judgements ident params))
  (read-record [_ id ident params]
    (handle-read state id ident params))
  (delete-record [_ id ident]
    (handle-delete state id ident))
  (update-record [_ id judgement ident]
    (handle-update state id judgement ident))
  (list-records [_ filter-map should-map ident params]
    (handle-list state filter-map should-map ident params))

  IJudgementStore
  (add-indicator-to-judgement [_ judgement-id indicator-rel ident]
    (handle-add-indicator-to state judgement-id indicator-rel ident))
  (list-judgements-by-observable [this observable ident params]
    (handle-list state {[:observable :type]  (:type observable)
                        [:observable :value] (:value observable)} {} ident params))
  (calculate-verdict [_ observable ident]
    (handle-calculate-verdict state observable ident))

  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
