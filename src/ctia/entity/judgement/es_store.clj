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
  "XFV-20: restrict verdict candidates to judgements owned by the caller's own
   org, so a foreign judgement shared via `authorized_*` cannot contribute to
   (poison) the verdict. Terms are lower-cased to match the `groups`
   `lowercase_normalizer` mapping; the arg is the ident *map*."
  [{:keys [groups]}]
  {:terms {"groups" (map str/lower-case groups)}})

(defn list-active-by-observable
  [state observable ident get-in-config params]
  {:pre [(contains? ident :groups)]}
  ;; XFV-20: a verdict is scoped to the caller's own org. An org-less caller
  ;; (`auth/orgless-ident?`) has no org to scope to, so bail out explicitly
  ;; rather than issue a `{:terms {"groups" ...}}` filter that matches no stored
  ;; doc and silently 404s. Static-auth org-less settings are warned at boot; a
  ;; JWT missing `org/id` is not, so for it this per-request `debugf` (off at the
  ;; shipped `info` level) is the only signal, carrying the observable so an
  ;; operator who raises the level can correlate the 404 with the request.
  (if (auth/orgless-ident? ident)
    (do (log/debugf "verdict skipped: caller %s has no org; a verdict is tenant-local (observable %s)"
                    (pr-str (:login ident))
                    (pr-str observable))
        nil)
    (let [now-str (time/format-date-time (time/timestamp))
          date-range (select-keys params [:from :to])
          time-opts {:now-str now-str :date-range date-range}
          ;; Compose the base access-control restriction as one opaque element
          ;; of the top-level `:filter` (the convention every other caller of
          ;; `find-restriction-query-part` follows, e.g. `make-search-query` in
          ;; `ctia.stores.es.crud`), alongside the mandatory owner-org filter,
          ;; and put the observable/time clauses in `:must`. This avoids reaching
          ;; into the helper's `[:bool :must]`/`[:bool :filter]` internals, so a
          ;; future clause added inside `find-restriction-query-part` cannot be
          ;; silently clobbered.
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
