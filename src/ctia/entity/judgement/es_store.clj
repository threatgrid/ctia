(ns ctia.entity.judgement.es-store
  (:require [ductile.document :refer [search-docs]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-momo.lib.time :as time]
            [ctia.auth :as auth]
            [ctia.entity.judgement.schemas
             :refer
             [PartialStoredJudgement StoredJudgement]]
            [ctia.schemas.core :refer [Verdict]]
            [ctia.store :refer [IJudgementStore IQueryStringSearchableStore IStore] :as store]
            [ctia.stores.es
             [store :refer [close-connections! def-es-store] :as es.store]
             [mapping :as em]
             [query :refer [active-judgements-by-observable-query find-restriction-query-part]]
             [schemas :refer [ESConnState]]]
            [ctim.schemas.common :refer [disposition-map]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]))

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
      :reason em/sortable-text
      :reason_uri em/token})}})

(def coerce-stored-judgement-list
  (c/coercer! [(s/maybe StoredJudgement)]
              sc/json-schema-coercion-matcher))

(defn list-judgements-by-observable [this observable ident params]
  (store/list-records this
                      {:all-of {[:observable :type]  (:type observable)
                                [:observable :value] (:value observable)}}
                      ident
                      params))

(defn- verdict-owner-filter
  "Restrict verdict candidates to judgements owned by the caller's own org.
   Terms are lower-cased to match the `groups` `lowercase_normalizer` mapping;
   the arg is the ident map. See verdict scoping in
   resources/ctia/public/doc/design.md."
  [{:keys [groups]}]
  {:terms {"groups" (map str/lower-case groups)}})

(defn list-active-by-observable
  [state observable ident get-in-config params]
  {:pre [(contains? ident :groups)]}
  ;; An org-less caller has no org to scope to, so return no verdict rather than
  ;; issue a filter that matches nothing. See verdict scoping in
  ;; resources/ctia/public/doc/design.md.
  (if (auth/orgless-ident? ident)
    (do (log/debugf "verdict skipped: caller %s has no org; a verdict is tenant-local (observable %s)"
                    (pr-str (:login ident))
                    (pr-str observable))
        nil)
    (let [now-str (time/format-date-time (time/timestamp))
          date-range (select-keys params [:from :to])
          time-opts {:now-str now-str :date-range date-range}
          ;; Put the base access-control restriction and the owner-org filter in
          ;; the top-level `:filter` (the convention other callers of
          ;; `find-restriction-query-part` follow) and the observable/time
          ;; clauses in `:must`, rather than reaching into the helper's internals.
          composed-query
          {:bool {:filter [(find-restriction-query-part ident get-in-config)
                           (verdict-owner-filter ident)]
                  :must (active-judgements-by-observable-query observable time-opts)}}
          es-params
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
                    composed-query
                    nil
                    es-params)
       :data
       coerce-stored-judgement-list))))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :disposition_name (get disposition-map (:disposition judgement))
   :judgement_id (:id judgement)
   :observable (:observable judgement)
   :valid_time (:valid_time judgement)})

(s/defn calculate-verdict :- (s/maybe Verdict)
  [{{{:keys [get-in-config]} :ConfigService} :services
    :as state} :- ESConnState
   observable
   ident
   params]
  (some-> (list-active-by-observable
           state
           observable
           ident
           get-in-config
           params)
          first
          make-verdict))

(def-es-store JudgementStore :judgement StoredJudgement PartialStoredJudgement
  :extra-impls
  [IJudgementStore
   (list-judgements-by-observable [this observable ident params]
     (list-judgements-by-observable this observable ident params))
   (calculate-verdict [this observable ident]
     (calculate-verdict (:state this) observable ident {}))
   (calculate-verdict [this observable ident params]
     (calculate-verdict (:state this) observable ident params))])
