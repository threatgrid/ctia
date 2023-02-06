(ns ctia.http.routes.common
  (:require [clj-http.headers :refer [canonicalize]]
            [clj-momo.lib.clj-time.core :as t]
            [clojure.string :as str]
            [ctia.schemas.core :refer [SearchExtensionTemplates SortExtensionTemplates]]
            [ctia.schemas.search-agg :refer [MetricResult
                                             RangeQueryOpt
                                             SearchQuery
                                             SearchQueryArgs]]
            [ctia.schemas.sorting :as sorting]
            [ring.swagger.schema :refer [describe]]
            [ring.util.codec :as codec]
            [ring.util.http-response :as http-res]
            [ring.util.http-status :refer [ok]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defn es-params->sort-extension-templates :- SortExtensionTemplates
  [es-params]
  (or (-> es-params meta :sort-extension-templates)
      {}))

(s/defn es-params->search-extension-templates :- SearchExtensionTemplates
  [es-params]
  (or (-> es-params meta :search-extension-templates)
      {}))

(def search-options [:sort_by
                     :sort_order
                     :offset
                     :limit
                     :fields
                     :search_after
                     :query_mode
                     :search_fields])

(def filter-map-search-options
  (conj search-options :query :simple_query :from :to))

(s/defschema DateRangeParams
  (st/optional-keys
    {:from s/Inst
     :to s/Inst}))

(s/defschema BaseEntityFilterParams
  (st/merge
   DateRangeParams
   (st/optional-keys
    {:id s/Str
     :revision s/Int
     :language s/Str
     :tlp s/Str})))

(s/defschema SourcableEntityFilterParams
  {(s/optional-key :source) s/Str})

(s/defschema SearchableEntityParams
  {(s/optional-key :query) s/Str

   (s/optional-key :simple_query)
   (describe s/Str "Query String with simple query format")})

(s/defschema PagingParams
  "A schema defining the accepted paging and sorting related query parameters."
  {(s/optional-key :sort_by) (describe (apply s/enum sorting/default-entity-sort-fields)
                                       "Sort results on a field")
   (s/optional-key :sort_order) (describe (s/enum :asc :desc) "Sort direction")
   (s/optional-key :offset) (describe Long "Pagination Offset")
   (s/optional-key :search_after) (describe [s/Str] "Pagination stateless cursor")
   (s/optional-key :limit) (describe Long "Pagination Limit")})

(s/defn prep-sort_by-param-schema :- (s/protocol s/Schema)
  "Generalize sort_by enum schema from (enum fields...) to s/Str
  so it can accept the `field1:asc,field2:desc` syntax to sort by
  multiple fields. Add the original enums to the doc for the route,
  that also explains how this syntax works."
  [search-q-params]
  (cond-> search-q-params
    (st/get-in search-q-params [:sort_by])
    (st/update :sort_by
               (fn [sort_by-enum]
                 (assert (instance? schema.core.EnumSchema sort_by-enum)
                         "Must map :sort_by schema to an s/enum of strings")
                 (let [valid-fields (:vs sort_by-enum)
                       _ (assert (every? (some-fn string? simple-ident?) valid-fields) (pr-str valid-fields))
                       valid-fields (sort (map name valid-fields))
                       example-field1 (or (first valid-fields) "field1")
                       example-field2 (or (second valid-fields) "field2")]
                   (describe s/Str
                             (str "Sort result on fields.\n\n"
                                  "The following fields are supported: "
                                  (str/join ", " valid-fields)
                                  "\n\n"
                                  "Fields can be combined with ',' and sort order can be specified by ':asc' and ':desc'. For example:\n\n"
                                  (format "-  %s       # sort by %s ascending\n" example-field1 example-field1)
                                  (format "-  %s:desc  # sort by %s descending\n" example-field1 example-field1)
                                  (format "-  %s,%s:desc  # sort by %s ascending, then %s descending" example-field1 example-field2 example-field1 example-field2))))))))

(s/defn prep-es-fields-schema :- (s/protocol s/Schema)
  "Conjoins Elasticsearch fields parameter into search-q-params schema"
  [{{:keys [get-store]} :StoreService}
   {:keys [search-q-params entity] :as _entity-crud-config}]
  (let [searchable-fields (-> entity get-store :state :searchable-fields)
        default-fields-schema (->> searchable-fields
                                   (map name)
                                   (apply s/enum))]
    (if (seq searchable-fields)
      (st/merge
       search-q-params
       {;; We cannot name the parameter :fields, because we already have :fields (part
        ;; of search-q-params). That key is to select a subsets of fields of the
        ;; retrieved document and it gets passed to the `_source` parameter of
        ;; Elasticsearch. For more: www.elastic.co/guide/en/elasticsearch/reference/current/mapping-source-field.html
        (s/optional-key :search_fields)
        (describe [default-fields-schema] "'fields' key of Elasticsearch Fulltext Query.")})
      search-q-params)))

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
  ([{:keys [date-field make-date-range-fn search-extension-templates]
     {:keys [query
             from to
             simple_query
             search_fields] :as search-params}
     :search-params
     :or {make-date-range-fn (s/fn :- RangeQueryOpt
                               [from :- (s/maybe s/Inst)
                                to :- (s/maybe s/Inst)]
                               (cond-> {}
                                 from (assoc :gte from)
                                 to   (assoc :lt to)))}}
    :- SearchQueryArgs]
   (let [filter-map (apply dissoc search-params filter-map-search-options (keys search-extension-templates))
         date-range (make-date-range-fn from to)
         concrete-range-extensions (mapv (fn [[ext-key ext-val]]
                                           (-> (get search-extension-templates ext-key)
                                               (assoc :ext-val ext-val)))
                                         (select-keys search-params (keys search-extension-templates)))]
     (cond-> {}
       (seq date-range)        (assoc-in [:range date-field] date-range)
       (seq filter-map)        (assoc :filter-map filter-map)
       (or query simple_query) (assoc :full-text
                                      (->> (cond-> []
                                             query        (conj {:query query, :query_mode :query_string})
                                             simple_query (conj {:query_mode :simple_query_string
                                                                 :query      simple_query}))
                                           (mapv #(merge % (when search_fields
                                                             {:fields search_fields})))))
       (seq concrete-range-extensions) (assoc :search-extensions concrete-range-extensions)))))

(s/defn format-agg-result :- MetricResult
  [result
   agg-type
   aggregate-on
   {:keys [range full-text filter-map]} :- SearchQuery]
  (let [full-text* (map #(assoc % :query_mode
                                (get % :query_mode :query_string)) full-text)
        nested-fields (map keyword (str/split (name aggregate-on) #"\."))
        {from :gte to :lt} (-> range first val)
        filters (cond-> {:from from :to to}
                  ;; TODO support range extensions
                  (seq filter-map) (into filter-map)
                  (seq full-text) (assoc :full-text full-text*))]
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
