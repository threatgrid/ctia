(ns ctia.stores.es.crud
  (:require [clj-momo.lib.es
             [document :as d]
             [query :as q]]
            [clojure.string :as string]
            [ctia.domain.access-control
             :refer
             [restricted-read?
              acl-fields
              allow-read?
              allow-write?]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.properties :as p]
            [ctia.schemas.search-agg :refer [SearchQuery
                                             HistogramQuery
                                             TopnQuery
                                             CardinalityQuery
                                             AggQuery]]
            [ctia.stores.es.query :refer [find-restriction-query-part]]
            [ctia.stores.es.schemas :refer [ESConnState]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]
            [schema-tools.core :as st]))

(defn make-es-read-params
  "Prepare ES Params for read operations, setting the _source field
   and including ACL mandatory ones."
  [{:keys [fields]
    :as params}]
  (if (coll? fields)
    (-> params
        (assoc :_source (concat fields acl-fields))
        (dissoc :fields))
    params))

(defn coerce-to-fn
  [Model]
  (c/coercer! Model sc/json-schema-coercion-matcher))

(defn ensure-document-id
  "Returns a document ID.  if id is a object ID, it extract the
  document ID, if it's a document ID already, it will just return
  that."
  [id]
  (let [[_orig docid] (re-matches #".*?([^/]+)\z" id) ]
    docid))

(defn ensure-document-id-in-map
  "Ensure a document ID in a given filter map"
  [{:keys [id] :as m}]
  (cond-> m
    id (update :id ensure-document-id)))

(defn remove-es-actions
  "Removes the ES action level

  [{:index {:_id \"1\"}}
   {:index {:_id \"2\"}}]

  ->

  [{:_id \"1\"}
   {:_id \"2\"}]
  "
  [items]
  (map (comp first vals) items))

(defn build-create-result
  [item coerce-fn]
  (-> item
      (dissoc :_id :_index :_type)
      coerce-fn))

(defn partial-results
  "Build partial results when an error occurs for one or more items
   in the bulk operation.

   Ex:

   [{model1}
    {:error \"Error message item2\"}
    {model3}]"
  [exception-data models coerce-fn]
  (let [{{:keys [_errors items]}
         :es-http-res-body} exception-data]
    {:data (map (fn [{:keys [error _id]} model]
                  (if error
                    {:error error
                     :id _id}
                    (build-create-result model coerce-fn)))
                (remove-es-actions items) models)}))


(s/defn get-docs-with-indices
  "Retrieves a documents from a search \"ids\" query. It enables to retrieves
 documents from an alias that points to multiple indices.
It returns the documents with full hits meta data including the real index in which is stored the document."
  [{:keys [conn index]} :- ESConnState
   mapping :- s/Keyword
   ids :- [s/Str]
   params]
  (let [ids-query (q/ids (map ensure-document-id ids))
        res (d/query conn
                     index
                     (name mapping)
                     ids-query
                     (assoc (make-es-read-params params)
                            :full-hits?
                            true))]
    (:data res)))

(s/defn get-doc-with-index
  "Retrieves a document from a search \"ids\" query. It is used to perform a get query on an alias that points to multiple indices.
 It returns the document with full hits meta data including the real index in which is stored the document."
  [conn-state :- ESConnState
   mapping :- s/Keyword
   _id :- s/Str
   params]
  (first (get-docs-with-indices conn-state mapping [_id] params)))

(defn handle-create
  "Generate an ES create handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe [Model])
      [{:keys [props] :as state} :- ESConnState
       models :- [Model]
       _ident
       {:keys [refresh]}]
      (try
        (map #(build-create-result % coerce!)
             (d/bulk-create-doc (:conn state)
                                (map #(assoc %
                                             :_id (:id %)
                                             :_index (:write-index props)
                                             :_type (name mapping))
                                     models)
                                (or refresh
                                    (:refresh props "false"))))
        (catch Exception e
          (throw
           (if-let [ex-data (ex-data e)]
             ;; Add partial results to the exception data map
             (ex-info (.getMessage e)
                      (partial-results ex-data models coerce!))
             e)))))))

(defn handle-update
  "Generate an ES update handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str
       realized :- Model
       ident
       {:keys [refresh]}]
      (when-let [{index :_index current-doc :_source}
                 (get-doc-with-index state mapping id {})]
        (if (allow-write? current-doc ident)
          (coerce! (d/index-doc (:conn state)
                                index
                                (name mapping)
                                (assoc realized
                                       :id (ensure-document-id id))
                                (or refresh
                                    (get-in state
                                            [:props :refresh]
                                            "false"))))
          (throw (ex-info "You are not allowed to update this document"
                          {:type :access-control-error})))))))

(defn handle-read
  "Generate an ES read handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [{{{:keys [get-in-config]} :ConfigService}
        :services
        :as state}
       :- ESConnState
       id :- s/Str
       ident
       params]
      (when-let [doc (-> (get-doc-with-index state
                                             mapping
                                             id
                                             (make-es-read-params params))
                         :_source
                         coerce!)]
        (if (allow-read? doc ident get-in-config)
          doc
          (throw (ex-info "You are not allowed to read this document"
                          {:type :access-control-error})))))))

(defn access-control-filter-list
  "Given an ident, keep only documents it is allowed to read"
  [docs ident get-in-config]
  (filter #(allow-read? % ident get-in-config) docs))

(defn handle-delete
  "Generate an ES delete handler using some mapping and schema"
  [mapping _Model]
  (s/fn :- s/Bool
    [state :- ESConnState
     id :- s/Str
     ident
     {:keys [refresh]}]
    (when-let [{index :_index doc :_source}
               (get-doc-with-index state mapping id {})]
      (if (allow-write? doc ident)
        (d/delete-doc (:conn state)
                      index
                      (name mapping)
                      (ensure-document-id id)
                      (or refresh
                          (get-in state
                                  [:props :refresh]
                                  "false")))
        (throw (ex-info "You are not allowed to delete this document"
                        {:type :access-control-error}))))))

(def default-sort-field "_doc,id")

(defn with-default-sort-field
  [params]
  (if (contains? params :sort_by)
    params
    (assoc params :sort_by default-sort-field)))

(s/defschema FilterSchema
  (st/optional-keys
   {:all-of {s/Any s/Any}
    :one-of {s/Any s/Any}
    :query s/Str}))

(def sort-fields-mapping
  "Mapping table for all fields which needs to be renamed
   for the sorting. Instead of using fielddata we can have
   a text field for full text searches, and an unanalysed keyword
   field with doc_values enabled for sorting"
  {"title" "title.whole"
   "reason" "reason.whole"})

(defn parse-sort-by
  "Parses the sort_by parameter
   Ex:
   \"title:ASC,revision:DESC\"
   ->
   [[\"title\" \"ASC\"] [\"revision\" \"DESC\"]]"
  [sort-by]
  (map
   (fn [field]
     (let [[x y] (string/split field #":")]
       (if y [x y] [x])))
   (string/split (name sort-by) #",")))

(defn format-sort-by
  "Format to the sort-by format
   Ex:
   [[\"title\" \"ASC\"] [\"revision\" \"DESC\"]]
   ->
   \"title:ASC,revision:DESC\""
  [sort-fields]
  (->> sort-fields
       (map (fn [field]
              (string/join ":" field)))
       (string/join ",")))

(defn rename-sort-fields
  "Renames sort fields based on the content of the `sort-fields-mapping` table."
  [{:keys [sort_by] :as params}]
  (if-let [updated-sort-by
           (some->> sort_by
                    parse-sort-by
                    (map (fn [[field-name :as field]]
                           (assoc field 0
                                  (get sort-fields-mapping field-name field-name))))
                    format-sort-by)]
    (assoc params :sort_by updated-sort-by)
    params))

(defn handle-find
  "Generate an ES find/list handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [{{{:keys [get-in-config]} :ConfigService}
        :services :as state} :- ESConnState
       {:keys [all-of one-of query]
        :or {all-of {} one-of {}}} :- FilterSchema
       ident
       params]
      (let [filter-val (cond-> (q/prepare-terms all-of)
                         (restricted-read? ident)
                         (conj (find-restriction-query-part ident get-in-config)))

            query_string  {:query_string {:query query}}
            bool-params (cond-> {:filter filter-val}
                          (seq one-of) (into
                                        {:should (q/prepare-terms one-of)
                                         :minimum_should_match 1})
                          query (update :filter conj query_string))]
        (cond-> (coerce! (d/query (:conn state)
                                  (:index state)
                                  (name mapping)
                                  (q/bool bool-params)
                                  (-> params
                                      rename-sort-fields
                                      with-default-sort-field
                                      make-es-read-params)))
          (restricted-read? ident) (update :data
                                           access-control-filter-list
                                           ident
                                           get-in-config))))))


(s/defn make-search-query
  [{{:keys [default_operator]} :props
    {{:keys [get-in-config]} :ConfigService} :services} :- ESConnState
   {:keys [query-string filter-map date-range]} :- SearchQuery
   ident]
  (let [es-query-string {:query_string (into {:query query-string}
                                             (when default_operator
                                               {:default_operator default_operator}))}
        date-range-query (when date-range
                           {:range date-range})
        filter-terms (-> (ensure-document-id-in-map filter-map)
                         q/prepare-terms)]
    {:bool
     {:filter
      (cond-> [(find-restriction-query-part ident get-in-config)]
        (seq filter-map) (into filter-terms)
        (seq date-range) (conj date-range-query)
        (seq query-string) (conj es-query-string))}}))

(defn handle-query-string-search
  "Generate an ES query handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [{conn :conn
        index :index
        {{:keys [get-in-config]} :ConfigService}
        :services
        :as es-conn-state} :- ESConnState
       {:keys [filter-map] :as search-query} :- SearchQuery
       ident
       params]
      (let [query (make-search-query es-conn-state search-query ident)]
        (cond-> (coerce! (d/query conn
                                  index
                                  (name mapping)
                                  query
                                  (-> params
                                      rename-sort-fields
                                      with-default-sort-field
                                      make-es-read-params)))
          (restricted-read? ident) (update :data
                                           access-control-filter-list
                                           ident
                                           get-in-config))))))

(defn handle-query-string-count
  "Generate an ES count handler using some mapping and schema"
  [mapping]
  (s/fn :- s/Int
    [{conn :conn
      index :index
      :as es-conn-state} :- ESConnState
     {:keys [filter-map] :as search-query} :- SearchQuery
     ident]
    (let [query (make-search-query es-conn-state search-query ident)]
      (d/count-docs conn
                    index
                    (name mapping)
                    query))))

(s/defn make-histogram
  [{:keys [aggregate-on granularity timezone]
    :or {timezone "+00:00"}} :- HistogramQuery]
  {:date_histogram
   {:field aggregate-on
    :interval granularity ;; TODO switch to calendar_interval with ES7
    :time_zone timezone}})

(s/defn make-topn
  [{:keys [aggregate-on limit sort_order]
    :or {limit 10 sort_order :desc}} :- TopnQuery]
  {:terms
   {:field aggregate-on
    :size limit
    :order {:_count sort_order}}})

(s/defn make-cardinality
  [{:keys [aggregate-on]} :- CardinalityQuery]
  {:cardinality {:field aggregate-on
                 :precision_threshold 10000}})

(s/defn make-aggregation
  [{:keys [agg-type] :as agg-query} :- AggQuery]
  (let [agg-fn
        (case agg-type
              :topn make-topn
              :cardinality make-cardinality
              :histogram make-histogram
              (throw (ex-info "invalid aggregation type" agg-type)))]
    {:metric (agg-fn agg-query)}))

(defn format-agg-result
  [agg-type
   {:keys [value buckets] :as _metric-res}]
  (case agg-type
    :cardinality value
    :topn (map #(array-map :key (:key %)
                           :value (:doc_count %))
               buckets)
    :histogram (map #(array-map :key (:key_as_string %)
                                :value (:doc_count %))
                    buckets)))

(defn handle-aggregate
  "Generate an ES aggregation handler using some mapping and schema"
  [mapping]
  (s/fn :- s/Any
    [{:keys [conn index] :as es-conn-state} :- ESConnState
     {:keys [filter-map] :as search-query} :- SearchQuery
     {:keys [agg-type] :as agg-query} :- AggQuery
     ident]
    (let [query (make-search-query es-conn-state search-query ident)
          agg (make-aggregation agg-query)
          es-res (d/query conn
                          index
                          (name mapping)
                          query
                          agg
                          {:limit 0})]
      (format-agg-result agg-type
                         (get-in es-res [:aggs :metric])))))
