(ns ctia.http.routes.common
  (:require
   [clojure.string :as str]
   [clj-http.headers :refer [canonicalize]]
   [ctia.schemas.sorting :as sorting]
   [ring.util.http-status :refer [ok]]
   [ring.util.http-response :as http-res]
   [schema.core :as s]
   [schema-tools.core :as st]
   [ring.swagger.schema :refer [describe]]))

(def search-options [:sort_by
                     :sort_order
                     :offset
                     :limit
                     :fields])

(def filter-map-search-options
  (conj search-options :query))

(def datatable-sort-fields
  (apply s/enum sorting/default-entity-sort-fields))

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

(def judgements-by-observable-sort-fields
  (apply s/enum (map name (conj sorting/judgement-sort-fields
                                "disposition:asc,valid_time.start_time:desc"))))

(def sighting-sort-fields
  (apply s/enum sorting/sighting-sort-fields))

(def actor-sort-fields
  (apply s/enum sorting/actor-sort-fields))

(def campaign-sort-fields
  (apply s/enum sorting/campaign-sort-fields))

(def coa-sort-fields
  (apply s/enum sorting/coa-sort-fields))

(def feedback-sort-fields
  (apply s/enum sorting/feedback-sort-fields))

(def attack-pattern-sort-fields
  (apply s/enum sorting/attack-pattern-sort-fields))

(def malware-sort-fields
  (apply s/enum sorting/malware-sort-fields))

(def tool-sort-fields
  (apply s/enum sorting/tool-sort-fields))

(def investigation-sort-fields
  (apply s/enum sorting/investigation-sort-fields))

(def investigation-select-fields
  (apply s/enum (concat sorting/investigation-sort-fields
                        [:description
                         :type
                         :search-txt
                         :short_description
                         :created_at])))

;; Paging related values and code

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum sorting/default-entity-sort-fields)
                                       "Sort results on a field")
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


;; actor

(s/defschema ActorFieldsParam
  {(s/optional-key :fields) [actor-sort-fields]})

(s/defschema ActorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   ActorFieldsParam
   {:query s/Str
    (s/optional-key :actor_type) s/Str
    (s/optional-key :motivation) s/Str
    (s/optional-key :sophistication) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  actor-sort-fields}))

(def ActorGetParams ActorFieldsParam)

(s/defschema ActorByExternalIdQueryParams
  (st/merge
   PagingParams
   ActorFieldsParam))

;; campaign

(s/defschema CampaignFieldsParam
  {(s/optional-key :fields) [campaign-sort-fields]})

(s/defschema CampaignSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   CampaignFieldsParam
   {:query s/Str
    (s/optional-key :campaign_type) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :activity) s/Str
    (s/optional-key :sort_by)  campaign-sort-fields}))

(def CampaignGetParams CampaignFieldsParam)

(s/defschema CampaignByExternalIdQueryParams
  (st/merge
   PagingParams
   CampaignFieldsParam))

;; COA

(s/defschema COAFieldsParam
  {(s/optional-key :fields) [coa-sort-fields]})

(s/defschema COASearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   COAFieldsParam
   {:query s/Str
    (s/optional-key :stage) s/Str
    (s/optional-key :coa_type) s/Str
    (s/optional-key :impact) s/Str
    (s/optional-key :objective) s/Str
    (s/optional-key :cost) s/Str
    (s/optional-key :efficacy) s/Str
    (s/optional-key :structured_coa_type) s/Str
    (s/optional-key :sort_by) coa-sort-fields}))

(def COAGetParams COAFieldsParam)

(s/defschema COAByExternalIdQueryParams
  (st/merge
   PagingParams
   COAFieldsParam))

;; data-table

(s/defschema DataTableFieldsParam
  {(s/optional-key :fields) [datatable-sort-fields]})

(def DataTableGetParams DataTableFieldsParam)

(s/defschema DataTableByExternalIdQueryParams
  (st/merge
   PagingParams
   DataTableFieldsParam))

;; exploit-target

(s/defschema ExploitTargetFieldsParam
  {(s/optional-key :fields) [exploit-target-sort-fields]})

(s/defschema ExploitTargetSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   ExploitTargetFieldsParam
   {:query s/Str
    (s/optional-key :sort_by) exploit-target-sort-fields}))

(def ExploitTargetGetParams ExploitTargetFieldsParam)

(s/defschema ExploitTargetByExternalIdQueryParams
  (st/merge
   PagingParams
   ExploitTargetFieldsParam))

;; feedback

(s/defschema FeedbackFieldsParam
  {(s/optional-key :fields) [feedback-sort-fields]})

(s/defschema FeedbackQueryParams
  (st/merge
   FeedbackFieldsParam
   PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) feedback-sort-fields}))

(def FeedbackGetParams FeedbackFieldsParam)

(s/defschema FeedbackByExternalIdQueryParams
  (st/dissoc FeedbackQueryParams :entity_id))

;; incident

(s/defschema IncidentFieldsParam
  {(s/optional-key :fields) [incident-sort-fields]})

(s/defschema IncidentSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IncidentFieldsParam
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

(def IncidentGetParams IncidentFieldsParam)

(s/defschema IncidentByExternalIdQueryParams
  (st/merge
   PagingParams
   IncidentFieldsParam))

;; indicator

(s/defschema IndicatorFieldsParam
  {(s/optional-key :fields) [indicator-sort-fields]})

(s/defschema IndicatorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IndicatorFieldsParam
   {:query s/Str
    (s/optional-key :indicator_type) s/Str
    (s/optional-key :tags) s/Int
    (s/optional-key :kill_chain_phases) s/Str
    (s/optional-key :producer) s/Str
    (s/optional-key :specification) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  indicator-sort-fields}))

(def IndicatorGetParams IndicatorFieldsParam)

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   IndicatorFieldsParam
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsByExternalIdQueryParams
  IndicatorsListQueryParams)

;; judgement

(s/defschema JudgementFieldsParam
  {(s/optional-key :fields) [judgement-sort-fields]})

(s/defschema JudgementSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   JudgementFieldsParam
   {:query s/Str
    (s/optional-key :disposition_name) s/Str
    (s/optional-key :disposition) s/Int
    (s/optional-key :priority) s/Int
    (s/optional-key :severity) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  judgement-sort-fields}))

(def JudgementGetParams JudgementFieldsParam)

(s/defschema FeedbacksByJudgementQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by) feedback-sort-fields}))

(s/defschema JudgementsQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by) judgement-sort-fields}))

(s/defschema JudgementsByExternalIdQueryParams
  (st/merge
   JudgementsQueryParams
   JudgementFieldsParam))

(s/defschema JudgementsByObservableQueryParams
  (st/merge
   PagingParams
   JudgementFieldsParam
   {(s/optional-key :sort_by) judgements-by-observable-sort-fields}))

;; relationship

(s/defschema RelationshipFieldsParam
  {(s/optional-key :fields) [relationship-sort-fields]})

(s/defschema RelationshipSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   RelationshipFieldsParam
   {:query s/Str
    (s/optional-key :relationship_type) s/Str
    (s/optional-key :source_ref) s/Str
    (s/optional-key :target_ref) s/Str
    (s/optional-key :sort_by)  relationship-sort-fields}))

(s/defschema RelationshipGetParams RelationshipFieldsParam)

(s/defschema RelationshipByExternalIdQueryParams
  (st/merge PagingParams
            RelationshipFieldsParam))

;; sighting

(s/defschema SightingFieldsParam
  {(s/optional-key :fields) [sighting-sort-fields]})

(s/defschema SightingSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   SightingFieldsParam
   {:query s/Str
    (s/optional-key :sensor) s/Str
    (s/optional-key :observables.value) s/Str
    (s/optional-key :observables.type) s/Str
    (s/optional-key :sort_by)  sighting-sort-fields}))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam
   {(s/optional-key :sort_by)
    (s/enum
     :id
     :timestamp
     :confidence
     :observed_time.start_time)}))

(def SightingGetParams SightingFieldsParam)

(s/defschema SightingByExternalIdQueryParams
  (st/merge
   PagingParams
   SightingFieldsParam))

;; attack-pattern

(s/defschema AttackPatternFieldsParam
  {(s/optional-key :fields) [attack-pattern-sort-fields]})

(s/defschema AttackPatternSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   AttackPatternFieldsParam
   {:query s/Str}
   (st/optional-keys
    {:kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name s/Str
     :sort_by attack-pattern-sort-fields})))

(s/defschema AttackPatternGetParams AttackPatternFieldsParam)

(s/defschema AttackPatternByExternalIdQueryParams
  (st/merge PagingParams AttackPatternFieldsParam))

;; malware

(s/defschema MalwareFieldsParam
  {(s/optional-key :fields) [malware-sort-fields]})

(s/defschema MalwareSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   MalwareFieldsParam
   {:query s/Str}
   (st/optional-keys
    {:labels s/Str
     :kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name s/Str
     :sort_by malware-sort-fields})))

(s/defschema MalwareGetParams MalwareFieldsParam)

(s/defschema MalwareByExternalIdQueryParams
  (st/merge PagingParams
            MalwareFieldsParam))

;; tool

(s/defschema ToolFieldsParam
  {(s/optional-key :fields) [tool-sort-fields]})

(s/defschema ToolSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   ToolFieldsParam
   {:query s/Str}
   (st/optional-keys
    {:labels s/Str
     :kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name s/Str
     :tool_version s/Str
     :sort_by malware-sort-fields})))

(s/defschema ToolGetParams ToolFieldsParam)

(s/defschema ToolByExternalIdQueryParams
  (st/merge PagingParams
            ToolFieldsParam))

;; investigation

(s/defschema InvestigationFieldsParam
  {(s/optional-key :fields) [investigation-select-fields]})

(s/defschema InvestigationSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   InvestigationFieldsParam
   {:query s/Str}
   {s/Keyword s/Any}))

(def InvestigationGetParams InvestigationFieldsParam)

(s/defschema InvestigationsByExternalIdQueryParams
  (st/merge
   InvestigationFieldsParam
   PagingParams))
