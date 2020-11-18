(ns ctia.http.routes.common
  (:require [clj-http.headers :refer [canonicalize]]
            [clojure.string :as str]
            [ctia.schemas.sorting :as sorting]
            [ring.swagger.schema :refer [describe]]
            [clj-momo.lib.clj-time.core :as t]
            [ring.util
             [codec :as codec]
             [http-response :as http-res]
             [http-status :refer [ok]]]
            [ctia.schemas.search-agg :refer [SearchQuery MetricResult]]
            [schema.core :as s]))

(def search-options [:sort_by
                     :sort_order
                     :offset
                     :limit
                     :fields
                     :search_after])

(def filter-map-search-options
  (conj search-options :query :from :to))

(s/defschema BaseEntityFilterParams
  {(s/optional-key :id) s/Str
   (s/optional-key :from) s/Inst
   (s/optional-key :to) s/Inst
   (s/optional-key :revision) s/Int
   (s/optional-key :language) s/Str
   (s/optional-key :tlp) s/Str})

(s/defschema SourcableEntityFilterParams
  {(s/optional-key :source) s/Str})

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum sorting/default-entity-sort-fields)
                                       "Sort results on a field")
   (s/optional-key :sort_order) (describe (s/enum :asc :desc) "Sort direction")
   (s/optional-key :offset) (describe Long "Pagination Offset")
   (s/optional-key :search_after) (describe [s/Str] "Pagination stateless cursor")
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
                            (codec/form-encode v)
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

(defn created
  "set a created response, using the id as the location header,
   and the full resource as body"
  [{:keys [id] :as resource}]
  (http-res/created id resource))

(defn now [] (java.util.Date.))

(s/defn coerce-date-range :- {:gte s/Inst
                              :lt s/Inst}
  "coerce from to limit interval querying to one year"
  [from :- s/Inst
   to :- (s/maybe s/Inst)]
  (let [to-or-now (or to (now))
        to-minus-one-year (t/minus to-or-now (t/years 1))
        from (t/latest from to-minus-one-year)]
    {:gte from
     :lt to-or-now}))

(s/defn search-query :- SearchQuery
  ([date-field search-params]
   (search-query date-field
                 search-params
                 (fn [from to]
                   (cond-> {}
                     from (assoc :gte from)
                     to (assoc :lt to)))))
  ([date-field
    {:keys [query from to] :as search-params}
    make-date-range-fn]
   (let [filter-map (apply dissoc search-params filter-map-search-options)
         date-range (make-date-range-fn from to)]
     (cond-> {}
       (seq date-range) (assoc-in [:date-range date-field] date-range)
       (seq filter-map) (assoc :filter-map filter-map)
       query (assoc :query-string query)))))

(s/defn format-agg-result :- MetricResult
  [result
   agg-type
   aggregate-on
   {:keys [date-range query-string filter-map]} :- SearchQuery]
  (let [nested-fields (map keyword
                            (str/split (name aggregate-on) #"\."))
        {from :gte to :lt} (-> date-range first val)
        filters (cond-> {:from from :to to}
                  (seq filter-map) (into filter-map)
                  (seq query-string) (assoc :query-string query-string))]
    {:data (assoc-in {} nested-fields result)
     :type agg-type
     :filters filters}))

(defn wait_for->refresh
  [wait_for]
  (case wait_for
    true {:refresh "wait_for"}
    false {:refresh "false"}
    {}))

(s/defschema Capability
  (s/conditional
    keyword? (s/pred simple-keyword?)
    nil? (s/pred nil?)
    set? #{(s/pred simple-keyword?)}))

(s/defn capabilities->string :- s/Str
  "Does not add leading or trailing new lines."
  [capabilities :- Capability]
  (cond
    (keyword? capabilities) (name capabilities)
    ((every-pred set? seq) capabilities) (->> capabilities
                                              sort
                                              (map name)
                                              (str/join ", "))
    :else (throw (ex-info "Missing capabilities!" {}))))

(s/defn capabilities->description :- s/Str
  "Does not add leading or trailing new lines."
  [capabilities :- Capability]
  (cond
    (keyword? capabilities) (str "Requires capability " (capabilities->string capabilities) ".")
    ((every-pred set? seq) capabilities) (str "Requires capabilities " (capabilities->string capabilities) ".")
    :else (throw (ex-info "Missing capabilities!" {}))))

(defmacro reloadable-function
  "Transforms v to (var v).

  This can help REPL development in several situations.

  When entities are top-level maps, :services->routes
  is fixed when the entity is evaluated.
  If passing :services->routes using a var deref,
  this means updates to the route function will
  not be observed until *both* the entity namespace and
  the server's routes have been reloaded.

  Using (var v) instead of v in top-level
  maps enables routes to dynamically
  update."
  [v]
  {:pre [(symbol? v)]}
  `(var ~v))
