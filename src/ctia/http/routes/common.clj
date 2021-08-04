(ns ctia.http.routes.common
  (:require
   [clj-http.headers :refer [canonicalize]]
   [clj-momo.lib.clj-time.core :as t]
   [clojure.set :as set]
   [clojure.string :as str]
   [ctia.schemas.search-agg
    :refer
    [FullTextQueryMode MetricResult RangeQueryOpt RawSearchParams SearchQuery]]
   [ctia.schemas.sorting :as sorting]
   [ring.swagger.schema :refer [describe]]
   [ring.util.codec :as codec]
   [ring.util.http-response :as http-res]
   [ring.util.http-status :refer [ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def search-options [:sort_by
                     :sort_order
                     :offset
                     :limit
                     :fields
                     :search_after
                     :query_mode
                     :search_fields])

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

(s/defschema SearchableEntityParams
  {(s/optional-key :query) s/Str

   (s/optional-key :query_mode)
   (describe FullTextQueryMode "Elasticsearch Fulltext Query Mode. Defaults to query_string")})

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum sorting/default-entity-sort-fields)
                                       "Sort results on a field")
   (s/optional-key :sort_order) (describe (s/enum :asc :desc) "Sort direction")
   (s/optional-key :offset) (describe Long "Pagination Offset")
   (s/optional-key :search_after) (describe [s/Str] "Pagination stateless cursor")
   (s/optional-key :limit) (describe Long "Pagination Limit")})

(def default-ignored-search-fields
  #{:language
    :revision
    :schema_version
    :source
    :source_uri
    :timestamp
    :tlp
    :type})

(def default-whitelisted-search-fields
  #{:id
    :title
    :short_description
    :description
    :external_ids})

(def ^:private SearchableFieldsParams
  {(s/required-key :fields) [s/Keyword]
   (s/optional-key :ignore) [s/Keyword]})

(s/defn searchable-fields :- (s/protocol s/Schema)
  "Takes the list of entity fields and (optionally) fields to ignore,
   and spits out enum schema to be used for :search_fields parameter.
   see also: `prep-es-fields-schema`"
  [{:keys [fields ignore]} :- SearchableFieldsParams]
  (let [ignored-fields (set/union
                        (set ignore)
                        default-ignored-search-fields)]
    (apply s/enum
           (-> fields
               set
               (set/union default-whitelisted-search-fields)
               (set/difference ignored-fields)))))

(s/defn prep-es-fields-schema :- (s/protocol s/Schema)
  "Conjoins Elasticsearch fields parameter into search-q-params schema"
  [{:keys [search-q-params
           searchable-fields] :as entity-crud-config}]
  (let [default-fields-schema (->>
                               searchable-fields
                               :vs
                               (map name)
                               (apply s/enum))]
    (if searchable-fields
     (st/merge
      search-q-params
      {;; We cannot name the parameter :fields, because we already have :fields (part
       ;; of search-q-params). That key is to select a subsets of fields of the
       ;; retrieved document and it gets passed to the `_source` parameter of
       ;; Elasticsearch. For more: www.elastic.co/guide/en/elasticsearch/reference/current/mapping-source-field.html
       (s/optional-key :search_fields)
       (describe [default-fields-schema] "'fields' key of Elasticsearch Fulltext Query.")})
     search-q-params)))

(s/defn enforce-search-fields
  "Gurantees that ES fields parameter always passed to ES instance"
  [{:keys [search_fields] :as query-params}
   searchable-fields]
  (if (seq search_fields)
    query-params
    (assoc
     query-params
     :search_fields
     (->> searchable-fields :vs (mapv name)))))

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

(s/defn now :- s/Inst
  []
  (java.util.Date.))

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
                 (s/fn :- RangeQueryOpt
                   [from :- (s/maybe s/Inst)
                    to :- (s/maybe s/Inst)]
                   (cond-> {}
                     from (assoc :gte from)
                     to   (assoc :lt to)))))
  ([date-field
    {:keys [query
            from to
            query_mode
            search_fields] :as search-params}
    make-date-range-fn :- (s/=> RangeQueryOpt
                                (s/named (s/maybe s/Inst) 'from)
                                (s/named (s/maybe s/Inst) 'to))]
   (let [filter-map (apply dissoc search-params filter-map-search-options)
         date-range (make-date-range-fn from to)]
     (cond-> {}
       (seq date-range) (assoc-in [:range date-field] date-range)
       (seq filter-map) (assoc :filter-map filter-map)
       query            (assoc :full-text
                               (merge
                                {:query      query
                                 :query_mode (or query_mode :query_string)}
                                (when search_fields
                                  {:fields search_fields})))))))

(s/defn format-agg-result :- MetricResult
  [result
   agg-type
   aggregate-on
   {:keys [range full-text filter-map]} :- SearchQuery]
  (let [full-text*         (assoc full-text :query_mode
                                  (get full-text :query_mode :query_string))
        nested-fields      (map keyword
                                (str/split (name aggregate-on) #"\."))
        {from :gte to :lt} (-> range first val)
        filters            (cond-> {:from from :to to}
                             (seq filter-map) (into filter-map)
                             (seq full-text)  (assoc :full-text full-text*))]
    {:data    (assoc-in {} nested-fields result)
     :type    agg-type
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
