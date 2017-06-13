(ns ctia.http.routes.common
  (:require [clojure.string :as str]
            [clj-http.headers :refer [canonicalize]]
            [ctia.schemas.sorting :as sorting]
            [ring.util.http-status :refer [ok]]
            [ring.util.http-response :as http-res]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ring.swagger.schema :refer [describe]]))


(def exploit-target-sort-fields
  (apply s/enum sorting/exploit-target-sort-fields))

(def incident-sort-fields
  (apply s/enum sorting/incident-sort-fields))

(def indicator-sort-fields
  (apply s/enum sorting/indicator-sort-fields))

(def relationship-sort-fields
  (apply s/enum sorting/relationship-sort-fields))

(def judgement-sort-fields
  (apply s/enum sorting/judgement-sort-fields))

(def sighting-sort-fields
  (apply s/enum sorting/sighting-sort-fields))

(def ttp-sort-fields
  (apply s/enum sorting/ttp-sort-fields))

(def actor-sort-fields
  (apply s/enum sorting/actor-sort-fields))

(def campaign-sort-fields
  (apply s/enum sorting/campaign-sort-fields))

(def coa-sort-fields
  (apply s/enum sorting/coa-sort-fields))

(def feedback-sort-fields
  (apply s/enum sorting/feedback-sort-fields))


;; Paging related values and code

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum sorting/default-entity-sort-fields) "Sort results on a field")
   (s/optional-key :sort_order) (describe (s/enum :asc :desc) "Sort direction")
   (s/optional-key :offset) (describe Long "Pagination Offset")
   (s/optional-key :limit) (describe Long "Pagination Limit")})

(def paging-param-keys
  "A list of the paging and sorting related parameters, we can use
  this to filter them out of query-param lists."
  (map :k (keys PagingParams)))


(defn map->paging-header-value [m]
  (str/join "&" (map (fn [[k v]]
                       (str (name k) "=" v)) m)))

(defn map->paging-headers
  "transform a map to a headers map
  {:total-hits 42}
  --> {'X-Total-Hits' '42'}"
  [headers]
  (reduce into {} (map (fn [[k v]]
                         {(->> k
                               name
                               (str "x-")
                               canonicalize)

                          (if (map? v)
                            (map->paging-header-value v)
                            (str v))}) headers)))

(defn paginated-ok
  "returns a 200 with the supplied response
   and its metas as headers"
  [{:keys [data paging]
    :or {data []
         paging {}}}]

  {:status ok
   :body data
   :headers (map->paging-headers paging)})

(defn created [{:keys [id] :as resource}]
  "set a created response, using the id as the location header,
   and the full resource as body"
  (http-res/created id resource))

;; These are the filter params, per entity.  We place them here since
;; they are used across entity routes.  For example, the
;; `ctia/indicator:ID/sightings/search` handler needs to know how to
;; filter Sightings.

(s/defschema BaseEntityFilterParams
  {(s/optional-key :id) s/Str
   (s/optional-key :revision) s/Int
   (s/optional-key :language) s/Str
   (s/optional-key :tlp) s/Str})

(s/defschema SourcableEntityFilterParams
  {(s/optional-key :source) s/Str})


(s/defschema ExploitTargetSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :sort_by) exploit-target-sort-fields}))

(s/defschema IncidentSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :status) s/Str
    (s/optional-key :reporter) s/Str
    (s/optional-key :coordinator) s/Str
    (s/optional-key :victim) s/Str
    (s/optional-key :security_compromise) s/Str
    (s/optional-key :discovery_method) s/Str
    (s/optional-key :contact) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :categories) s/Str
    (s/optional-key :sort_by) incident-sort-fields}))

(s/defschema IndicatorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :indicator_type) s/Str
    (s/optional-key :tags) s/Int
    (s/optional-key :kill_chain_phases) s/Str
    (s/optional-key :producer) s/Str
    (s/optional-key :specification) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  indicator-sort-fields}))

(s/defschema JudgementSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :disposition_name) s/Str
    (s/optional-key :disposition) s/Int
    (s/optional-key :priority) s/Int
    (s/optional-key :severity) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  judgement-sort-fields}))

(s/defschema RelationshipSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :relationship_type) s/Str
    (s/optional-key :source_ref) s/Str
    (s/optional-key :target_ref) s/Str
    (s/optional-key :sort_by)  relationship-sort-fields}))

(s/defschema SightingSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :sensor) s/Str
    (s/optional-key :observables.value) s/Str
    (s/optional-key :observables.type) s/Str
    (s/optional-key :sort_by)  sighting-sort-fields}))


(s/defschema TTPSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :ttp_type) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :behavior.malware_type.type) s/Str
    (s/optional-key :behavior.attack_pattern.capec_id) s/Str

    (s/optional-key :resource.personas) s/Str
    (s/optional-key :resource.tools.type) s/Str
    (s/optional-key :resource.tools.vendor) s/Str
    (s/optional-key :resource.tools.service_pack) s/Str
    (s/optional-key :resource.infrastructure.type) s/Str

    (s/optional-key :victim_targeting.identity) s/Str
    (s/optional-key :victim_targeting) s/Str

    (s/optional-key :kill_chains) s/Str

    (s/optional-key :sort_by)  ttp-sort-fields}))


(s/defschema ActorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :actor_type) s/Str
    (s/optional-key :motivation) s/Str
    (s/optional-key :sophistication) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  actor-sort-fields}))

(s/defschema CampaignSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :campaign_type) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :activity) s/Str
    (s/optional-key :sort_by)  campaign-sort-fields}))


(s/defschema COASearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {:query s/Str
    (s/optional-key :stage) s/Str
    (s/optional-key :coa_type) s/Str
    (s/optional-key :impact) s/Str
    (s/optional-key :objective) s/Str
    (s/optional-key :cost) s/Str
    (s/optional-key :efficacy) s/Str
    (s/optional-key :structured_coa_type) s/Str
    (s/optional-key :sort_by) coa-sort-fields}))
