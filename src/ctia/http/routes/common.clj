(ns ctia.http.routes.common
  (:require [clojure.string :as str]
            [clj-http.headers :refer [canonicalize]]
            [ring.util.http-status :refer [ok]]
            [ring.util.http-response :as http-res]
            [schema.core :as s]
            [schema-tools.core :as st]
            [ring.swagger.schema :refer [describe]]))


;; These are the fields that are sortable, per entity.  We place them
;; here since they are used by more than one entity's routes.  For
;; isntance, the indicator route needs to know how to sort sightings
;; for the `ctia/indicator/:ID/sighting` handler
;;
(def base-entity-sort-fields [:id :schema_version :revision
                              :timestamp :language :tlp])

(def sourcable-entity-sort-fields [:source :source_uri])

(def describable-entity-sort-fields [:title])

(def default-entity-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          describable-entity-sort-fields))

(def indicator-sort-fields
  (apply s/enum (concat default-entity-sort-fields
                        [:indicator_type
                         :likely_impact
                         :confidence
                         :specification])))

(def judgement-sort-fields
  (apply s/enum (concat default-entity-sort-fields
                        [:disposition
                         :priority
                         :confidence
                         :severity
                         :valid_time.start
                         :valid_time.end
                         :reason
                         :observable.type
                         :observable.value])))


(def sighting-sort-fields
  (apply s/enum (concat default-entity-sort-fields
                        [:observed_time.start
                         :observed_time.end
                         :confidence
                         :count
                         :sensor])))

(def feedback-sort-fields
  (apply s/enum (concat base-entity-sort-fields
                        sourcable-entity-sort-fields
                        [:entity_id
                         :feedback
                         :reason])))


;; Paging related values and code

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum default-entity-sort-fields) "Sort results on a field")
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

(s/defschema IndicatorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   {(s/optional-key :query) s/Str
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
   {(s/optional-key :query) s/Str
    (s/optional-key :disposition_name) s/Str
    (s/optional-key :disposition) s/Int
    (s/optional-key :priority) s/Int
    (s/optional-key :severity) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  judgement-sort-fields}))


