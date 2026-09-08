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
  "XFV-20: a verdict is a tenant-local trust decision. Restrict verdict
   candidates to judgements owned by the caller's own org (`groups`), so a
   judgement that merely lists the caller's org in `authorized_groups` /
   `authorized_users` cannot contribute to (poison) the caller's verdict.

   This neutralizes any pre-existing cross-tenant *read* grant for verdict
   calculation, regardless of the `authorized_*` clauses in
   `find-restriction-query-part`. It does NOT touch the *write* path: a
   foreign org listed in a victim-owned document's `authorized_groups` can
   still update that document (`allow-write?` in
   `ctia.domain.access-control`), and the stored owner `groups` are preserved
   on update (`default-realize`), so tightening write-side authority is a
   separate XFV-20 follow-up out of scope here."
  [{:keys [groups]}]
  {:terms {"groups" (map str/lower-case groups)}})

(defn list-active-by-observable
  [state observable ident get-in-config params]
  ;; XFV-20 (CR1): a verdict is scoped to the caller's own org. A caller with
  ;; no org (static-auth with `ctia.auth.static.group` unset, or a JWT missing
  ;; `org/id`) has no tenant to scope to, so there is no verdict to compute.
  ;; The anonymous read-only identity (`ctia.auth.type=static` +
  ;; `readonly-for-anonymous=true`) reports the sentinel
  ;; `auth/not-logged-in-groups` (`["Unknown Group"]`) rather than an empty
  ;; group list; it is likewise not a real tenant, so treat it as no-org too.
  ;; Bail out explicitly and observably rather than issuing a query whose
  ;; `{:terms {"groups" ...}}` filter can match no stored doc, silently
  ;; flipping the response to a 404 with nothing logged.
  (if (or (empty? (:groups ident))
          (= (set auth/not-logged-in-groups) (set (:groups ident))))
    (do (log/infof "verdict skipped: caller %s has no org; a verdict is tenant-local"
                   (pr-str (:login ident)))
        nil)
    (let [now-str (time/format-date-time (time/timestamp))
          date-range (select-keys params [:from :to])
          time-opts {:now-str now-str :date-range date-range}
          composed-query
          (-> (find-restriction-query-part ident get-in-config)
              (assoc-in
               [:bool :must]
               (active-judgements-by-observable-query
                observable
                time-opts))
              (update-in
               [:bool :filter]
               (fnil conj [])
               (verdict-owner-filter ident)))
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
