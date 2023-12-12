(ns ctia.stores.es.crud
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [ctia.domain.access-control :as ac
    :refer [allow-read? allow-write? restricted-read?]]
   [ctia.lib.pagination :refer [list-response-schema]]
   [ctia.schemas.core :refer [SortExtension SortExtensionDefinitions]]
   [ctia.schemas.search-agg
    :refer [AverageQuery AggQuery CardinalityQuery HistogramQuery QueryStringSearchArgs SearchQuery TopnQuery]]
   [ctia.stores.es.sort :as es.sort]
   [ctia.stores.es.query :as es.query]
   [ctia.stores.es.schemas :refer [ESConnState]]
   [ductile.document :as ductile.doc]
   [ductile.query :as q]
   [ring.swagger.coerce :as sc]
   [schema-tools.core :as st]
   [schema.coerce :as c]
   [schema.core :as s]))

(defn make-es-read-params
  "Prepare ES Params for read operations, setting the _source field
   and including ACL mandatory ones."
  [{:keys [fields]
    :as es-params}]
  (cond-> es-params
    (coll? fields)
    (-> (assoc :_source (concat fields ac/acl-fields))
        (dissoc :fields))))

(defn coerce-to-fn
  [Model]
  (c/coercer! Model sc/json-schema-coercion-matcher))

(defn ensure-document-id
  "Returns a document ID.  if id is a object ID, it extract the
  document ID, if it's a document ID already, it will just return
  that."
  [id]
  (let [[_orig docid] (re-matches #".*?([^/]+)\z" id)]
    docid))

(defn ensure-document-id-in-map
  "Ensure a document ID in a given filter map"
  [{:keys [id] :as m}]
  (cond-> m
    (string? id) (update :id list)
    id (update :id #(map ensure-document-id %))))

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
  (let [{{:keys [items]}
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
  [{:keys [conn index] :as _conn-state} :- ESConnState
   ids :- [s/Str]
   es-params]
  (let [limit (count ids)
        ids-query (q/ids (map ensure-document-id ids))
        res (ductile.doc/query conn
                               index
                               ids-query
                               (assoc (make-es-read-params es-params)
                                      :limit limit
                                      :full-hits? true))]
    (:data res)))

(s/defn get-doc-with-index
  "Retrieves a document from a search \"ids\" query. It is used to perform a get query on an alias that points to multiple indices.
 It returns the document with full hits meta data including the real index in which is stored the document."
  [conn-state :- ESConnState
   _id :- s/Str
   es-params]
  (first (get-docs-with-indices conn-state [_id] es-params)))

(defn ^:private prepare-opts
  [{:keys [props]}
   {:keys [refresh]}]
  {:refresh (or refresh
                (:refresh props)
                "false")})

(s/defn bulk-schema
  [Model :- (s/pred map?)]
  (st/optional-keys
   {:create [Model]
    :index [Model]
    :update [(st/optional-keys Model)]
    :delete [s/Str]}))

(s/defn ^:private prepare-bulk-doc
  [{:keys [props]} :- ESConnState
   mapping :- s/Keyword
   doc :- (s/pred map?)]
  (assoc doc
         :_id (:id doc)
         :_index (:write-index props)
         :_type (name mapping)))

(s/defn build-stored-transformer
  :- (s/=> s/Any s/Any) ;(s/=> (s/maybe out) (s/maybe in))
  ([transformer in out] (build-stored-transformer transformer in out {}))
  ([transformer :- (s/=> s/Any {:doc s/Any}) ;;(s/=> out {:doc in})
    in :- (s/protocol s/Schema)
    out :- (s/protocol s/Schema)
    opts :- {(s/optional-key :prev) s/Any}]
   (s/fn :- (s/maybe out)
     [doc :- (s/maybe in)]
     (when (some? doc)
       (transformer (assoc opts :doc doc))))))

(s/defn handle-create
  "Generate an ES create handler using some mapping and schema"
  ([mapping stored-schema]
   (handle-create mapping stored-schema
                  {:stored->es-stored :doc
                   :es-stored->stored :doc
                   :es-stored-schema stored-schema}))
  ([mapping stored-schema
    {:keys [stored->es-stored
            es-stored->stored
            es-stored-schema]}
    :- {:stored->es-stored (s/=> s/Any {:doc s/Any}) ;(s/=> es-stored-schema {:doc stored-schema})
        :es-stored->stored (s/=> s/Any {:doc s/Any}) ;(s/=> stored-schema {:doc es-stored-schema})
        :es-stored-schema (s/protocol s/Schema)}]
   (let [stored->es-stored (build-stored-transformer stored->es-stored stored-schema es-stored-schema) 
         es-stored->stored (build-stored-transformer es-stored->stored es-stored-schema stored-schema)
         coerce! (comp es-stored->stored
                       (coerce-to-fn (s/maybe es-stored-schema)))]
     (s/fn :- [stored-schema]
       [{:keys [conn] :as conn-state} :- ESConnState
        docs :- [stored-schema]
        _ident
        es-params]
       (let [prepare-doc #(prepare-bulk-doc conn-state mapping (stored->es-stored %))
             prepared (mapv prepare-doc docs)]
         (try
           (ductile.doc/bulk-index-docs conn
                                        prepared
                                        (prepare-opts conn-state es-params))
           docs
           (catch Exception e
             (throw
               (if-let [ex-data (ex-data e)]
                 ;; Add partial results to the exception data map
                 (ex-info (.getMessage e)
                          (partial-results ex-data docs coerce!))
                 e)))))))))

(s/defschema Update1MapArg
  {:stored->es-stored (s/=> s/Any {:doc s/Any}) ;(s/=> es-stored-schema {:doc stored-schema})
   :es-stored-schema (s/protocol s/Schema)})

(s/defn update1-default :- Update1MapArg
  [stored-schema]
  {:stored->es-stored :doc
   :es-stored-schema stored-schema})

(s/defn handle-update
  "Generate an ES update handler using some mapping and schema"
  ([mapping stored-schema]
   (handle-update mapping stored-schema (update1-default stored-schema)))
  ([mapping
    stored-schema :- (s/protocol s/Schema)
    {:keys [stored->es-stored
            es-stored-schema]} :- Update1MapArg]
   (let [coerce! (coerce-to-fn (s/maybe stored-schema))
         es-coerce! (coerce-to-fn es-stored-schema)]
     (s/fn :- (s/maybe stored-schema)
       [{:keys [conn] :as conn-state} :- ESConnState
        id :- s/Str
        realized :- stored-schema
        ident
        es-params]
       (when-let [[{index :_index current-doc :_source}]
                  (get-docs-with-indices conn-state [id] {})]
         (if (allow-write? current-doc ident)
           (let [update-doc (assoc realized
                                   :id (ensure-document-id id))
                 stored->es-stored (build-stored-transformer stored->es-stored stored-schema es-stored-schema
                                                             {:prev (es-coerce! current-doc)})]
             (ductile.doc/index-doc conn
                                    index
                                    (name mapping)
                                    (stored->es-stored update-doc)
                                    (prepare-opts conn-state es-params))
             (coerce! update-doc))
           (throw (ex-info "You are not allowed to update this document"
                           {:type :access-control-error}))))))))

(s/defschema Read1MapArg
  {:es-partial-stored-schema (s/protocol s/Schema)
   ; (s/=> partial-stored-schema {:doc es-partial-stored-schema})
   :es-partial-stored->partial-stored (s/=> s/Any {:doc s/Any})})

(s/defn read1-map-default :- Read1MapArg
  [partial-stored-schema]
  {:es-partial-stored-schema partial-stored-schema
   :es-partial-stored->partial-stored :doc})

(s/defn handle-read
  "Generate an ES read handler using some mapping and schema"
  ([partial-stored-schema]
   (handle-read partial-stored-schema (read1-map-default partial-stored-schema)))
  ([partial-stored-schema
    {:keys [es-partial-stored-schema es-partial-stored->partial-stored]} :- Read1MapArg]
   (let [es-partial-stored->partial-stored (build-stored-transformer
                                             es-partial-stored->partial-stored
                                             es-partial-stored-schema
                                             partial-stored-schema)
         coerce! (coerce-to-fn (s/maybe es-partial-stored-schema))]
     (s/fn :- (s/maybe partial-stored-schema)
       [{{{:keys [get-in-config]} :ConfigService}
         :services
         :as conn-state}
        :- ESConnState
        id :- s/Str
        ident
        {:keys [suppress-access-control-error?]
         :or {suppress-access-control-error? false}
         :as es-params}]
       (when-let [doc (-> (get-doc-with-index conn-state
                                              id
                                              (make-es-read-params es-params))
                          :_source
                          coerce!)]
         (if (allow-read? doc ident get-in-config)
           (es-partial-stored->partial-stored doc)
           (let [ex (ex-info "You are not allowed to read this document"
                             {:type :access-control-error})]
             (if suppress-access-control-error?
               (log/error ex)
               (throw ex)))))))))

(s/defn handle-read-many
  "Generate an ES read-many handler using some mapping and schema"
  ([partial-stored-schema]
   (handle-read-many partial-stored-schema (read1-map-default partial-stored-schema)))
  ([partial-stored-schema
    {:keys [es-partial-stored-schema es-partial-stored->partial-stored]} :- Read1MapArg]
   (let [es-partial-stored->partial-stored (build-stored-transformer
                                             es-partial-stored->partial-stored
                                             es-partial-stored-schema
                                             partial-stored-schema)
         coerce! (coerce-to-fn es-partial-stored-schema)]
     (s/fn :- [(s/maybe partial-stored-schema)]
       [{{{:keys [get-in-config]} :ConfigService}
         :services
         :as conn-state}
        :- ESConnState
        ids :- [s/Str]
        ident
        {:keys [suppress-access-control-error?]
         :or {suppress-access-control-error? false}
         :as es-params}]
       (sequence
         ;;TODO factor out following copied code from handle-read
         (comp (map :_source)
               (map coerce!)
               (map (fn [record]
                      (if (allow-read? record ident get-in-config)
                        (es-partial-stored->partial-stored record)
                        (let [ex (ex-info "You are not allowed to read this document"
                                          {:type :access-control-error})]
                          (if suppress-access-control-error?
                            (log/error ex)
                            (throw ex)))))))
         (get-docs-with-indices conn-state ids (make-es-read-params es-params)))))))

(defn access-control-filter-list
  "Given an ident, keep only documents it is allowed to read"
  [docs ident get-in-config]
  (filter #(allow-read? % ident get-in-config) docs))

(s/defschema BulkResult
  (st/optional-keys
   {:deleted [s/Str]
    :updated [s/Str]
    :errors (st/optional-keys
             {:forbidden [s/Str]
              :not-found [s/Str]
              :internal-error [s/Str]})}))

(s/defschema ESActionResult
  (st/open-schema
   {:_id s/Str
    :_index s/Str
    :status s/Int
    :result s/Str}))

;; TODO move it to ductile
(s/defschema ESBulkRes
  {:took s/Int
   :errors s/Bool
   :items [{ductile.doc/BulkOps ESActionResult}]})

(s/defn ^:private format-bulk-res
  "transform an elasticsearch bulk result into a CTIA Bulk Result.
   ex: https://www.elastic.co/guide/en/elasticsearch/reference/7.x/docs-bulk.html#docs-bulk-api-example"
  [bulk-res :- ESBulkRes]
  (let [{:keys [deleted updated not_found]}
        (->> (:items bulk-res)
             (map (comp first vals))
             (group-by :result)
             (into {}
                   (map (fn [[result items]]
                          {(keyword result) (map :_id items)}))))]
    (cond-> {}
      deleted (assoc :deleted deleted)
      updated (assoc :updated updated)
      not_found (assoc-in [:errors :not-found] not_found))))

(s/defn check-and-prepare-bulk
  :- (st/assoc BulkResult
               (s/optional-key :prepared)
               [(s/pred map?)])
  "prepare a bulk query:
  - retrieve actual indices, deletion cannot be performed on the alias.
  - filter out forbidden entitites
  - forbidden and not_found errors are prepared for the response."
  [conn-state :- ESConnState
   ids :- [s/Str]
   ident]
  (let [get-in-config (get-in conn-state [:services :ConfigService])
        doc-ids (map ensure-document-id ids)
        docs-with-indices (get-docs-with-indices conn-state doc-ids {})
        {authorized true forbidden-write false}
        (group-by #(allow-write? (:_source %) ident)
                  docs-with-indices)
        {forbidden true not-visible false}
        (group-by #(allow-read? (:_source %) ident get-in-config)
                  forbidden-write)
        missing (set/difference (set doc-ids)
                                (set (map :_id docs-with-indices)))
        not-found (into (map :_id not-visible) missing)
        prepared-docs (map #(select-keys % [:_index :_type :_id])
                           authorized)]
    (cond-> {}
      forbidden (assoc-in [:errors :forbidden] (map :_id forbidden))
      (seq not-found) (assoc-in [:errors :not-found] not-found)
      authorized (assoc :prepared prepared-docs))))

(s/defn bulk-delete :- BulkResult
  [{:keys [conn] :as conn-state}
   ids :- [s/Str]
   ident
   es-params]
  (let [{:keys [prepared errors]} (check-and-prepare-bulk conn-state ids ident)
        bulk-res (when prepared
                   (try
                     (format-bulk-res
                      (ductile.doc/bulk-delete-docs conn
                                                    prepared
                                                    (prepare-opts conn-state es-params)))
                     (catch Exception e
                       (log/error e
                                  (str "bulk delete failed: " (.getMessage e))
                                  (pr-str prepared))
                       {:errors {:internal-error (map :_id prepared)}})))]
    (cond-> bulk-res
      errors (update :errors
                     #(merge-with concat errors %)))))

(s/defn bulk-update
  "Generate an ES bulk update handler using some mapping and schema"
  ([stored-schema]
   (bulk-update stored-schema (update1-default stored-schema)))
  ([stored-schema :- (s/protocol s/Schema)
    {:keys [es-stored-schema
            stored->es-stored]} :- Update1MapArg]
   (let [stored->es-stored (build-stored-transformer stored->es-stored stored-schema es-stored-schema)]
     (s/fn :- BulkResult
       [{:keys [conn] :as conn-state}
        docs :- [stored-schema]
        ident
        es-params]
       (let [docs (map stored->es-stored docs)
             by-id (group-by :id docs)
             ids (seq (keys by-id))
             {:keys [prepared errors]} (check-and-prepare-bulk conn-state
                                                               ids
                                                               ident)
             prepared-docs (map (fn [meta]
                                  (-> (:_id meta)
                                      by-id
                                      first
                                      (into meta)))
                                prepared)
             bulk-res (if-not prepared
                        {}
                        (try
                          (format-bulk-res
                            (ductile.doc/bulk-index-docs conn
                                                         prepared-docs
                                                         (prepare-opts conn-state es-params)))
                          (catch Exception e
                            (log/error (str "bulk update failed: " (.getMessage e))
                                       (pr-str prepared))
                            {:errors {:internal-error (map :_id prepared)}})))]
         (cond-> bulk-res
           errors (update :errors
                          #(merge-with concat errors %))))))))

(defn handle-delete
  "Generate an ES delete handler using some mapping"
  [mapping]
  (s/fn :- s/Bool
    [{:keys [conn] :as conn-state} :- ESConnState
     id :- s/Str
     ident
     es-params]
    (if-let [{index :_index doc :_source}
             (get-doc-with-index conn-state id {})]
      (if (allow-write? doc ident)
        (ductile.doc/delete-doc conn
                                index
                                (name mapping)
                                (ensure-document-id id)
                                (prepare-opts conn-state es-params))
        (throw (ex-info "You are not allowed to delete this document"
                        {:type :access-control-error})))
      false)))

(s/defschema FilterSchema
  (st/optional-keys
   {:all-of {s/Any s/Any}
    :one-of {s/Any s/Any}
    :query s/Str}))

(def enumerable-fields-mapping
  "Mapping table for all fields which needs to be renamed
   for the sorting or aggregation. Instead of using fielddata we can have
   a text field for full text searches, and an unanalysed keyword
   field with doc_values enabled for sorting or aggregation"
  {"title" "title.whole"
   "reason" "reason.whole"})

(s/defn parse-sort-by :- [SortExtension]
  "Parses the sort_by parameter
   Ex:
   \"title:ASC,revision:DESC\"
   ->
   [{:op :field :field-name \"title\" :sort_order \"ASC\"}
    {:op :field :field-name \"revision\" :sort_order \"DESC\"}]"
  [sort_by]
  (if ((some-fn string? simple-ident?) sort_by)
    (map
      (fn [field]
        (let [[field-name field-order] (string/split field #":")]
          (cond-> {:op :field
                   :field-name (keyword field-name)}
            field-order (assoc :sort_order field-order))))
      (string/split (name sort_by) #","))
    sort_by))

(defn with-default-sort-field
  [es-params {:keys [default-sort]}]
  (assert (not (:sort_by es-params)))
  (update es-params :sort #(or %
                               (some->> default-sort
                                        parse-sort-by
                                        (mapv (fn [m] (es.sort/parse-sort-params-op m :asc))))
                               [{"_doc" :asc} {"id" :asc}])))

(s/defn rename-sort-fields
  "Renames sort fields based on the content of the `enumerable-fields-mapping` table
  and remaps to script extensions."
  [{:keys [sort_by sort_order] :as es-params}
   sort-extension-definitions :- (s/maybe SortExtensionDefinitions)]
  (cond-> (dissoc es-params :sort_by :sort_order)
    (and sort_by (not (:sort es-params)))
    (assoc :sort
           (->> sort_by
                parse-sort-by
                (mapv (fn [field]
                        {:pre [(= :field (:op field))]}
                        (let [{:keys [field-name] :as field}
                              (update field :field-name #(or (keyword (enumerable-fields-mapping (name %)))
                                                             %))]
                          (assert (simple-keyword? field-name))
                          (-> (or (some-> (get sort-extension-definitions field-name)
                                          (into (select-keys field [:sort_order]))
                                          (update :field-name #(or % (:field-name field))))
                                  field)
                              (es.sort/parse-sort-params-op (or sort_order :asc))))))))))

(s/defschema MakeQueryParamsArgs
  {:params s/Any
   :props s/Any
   (s/optional-key :sort-extension-definitions) SortExtensionDefinitions})

(defn es-seven-configured?
  "given ES store properties, check it is configured to use ES7"
  [{:keys [version]}]
  (<= 7 version))

(s/defn make-query-params :- {s/Keyword s/Any}
  [{:keys [params props sort-extension-definitions]} :- MakeQueryParamsArgs]
  (let [pres-k :allow_partial_search_results]
    (cond-> (-> params
                (rename-sort-fields sort-extension-definitions)
                (with-default-sort-field props)
                make-es-read-params)
      (and (es-seven-configured? props) (contains? props pres-k))
      (assoc pres-k (pres-k props))
      (es-seven-configured? props) (assoc :track_total_hits true))))

(defn list-response-coercer [es-partial-stored-schema
                             es-partial-stored->partial-stored]
  (comp #(update % :data (fn [docs] (mapv es-partial-stored->partial-stored docs)))
        (coerce-to-fn (list-response-schema es-partial-stored-schema))))

(s/defn handle-find
  "Generate an ES find/list handler using some mapping and schema"
  ([partial-stored-schema]
   (handle-find partial-stored-schema (read1-map-default partial-stored-schema)))
  ([partial-stored-schema :- (s/protocol s/Schema)
    {:keys [es-partial-stored-schema
            es-partial-stored->partial-stored]} :- Read1MapArg]
   (let [es-partial-stored->partial-stored (build-stored-transformer es-partial-stored->partial-stored
                                                                     es-partial-stored-schema
                                                                     partial-stored-schema)
         coerce! (list-response-coercer es-partial-stored-schema
                                        es-partial-stored->partial-stored )]
     (s/fn :- (list-response-schema partial-stored-schema)
       [{{{:keys [get-in-config]} :ConfigService} :services
         :keys [conn index props]} :- ESConnState
        {:keys [all-of one-of query]
         :or {all-of {} one-of {}}} :- FilterSchema
        ident
        es-params]
       (let [filter-val (cond-> (q/prepare-terms all-of)
                          (restricted-read? ident)
                          (conj (es.query/find-restriction-query-part ident get-in-config)))
             query_string  {:query_string {:query query}}
             date-range-query (es.query/make-date-range-query es-params)
             bool-params (cond-> {:filter filter-val}
                           (seq one-of) (into
                                          {:should (q/prepare-terms one-of)
                                           :minimum_should_match 1})
                           query (update :filter conj query_string)
                           (seq date-range-query) (update :filter conj {:range date-range-query}))
             query-params (make-query-params {:params es-params :props props})]
         (cond-> (coerce! (ductile.doc/query conn
                                             index
                                             (q/bool bool-params)
                                             query-params))
           (restricted-read? ident) (update :data
                                            access-control-filter-list
                                            ident
                                            get-in-config)))))))

(s/defn make-search-query :- {s/Keyword s/Any}
  "Translate SearchQuery map into ES Query DSL map"
  ([es-conn-state :- ESConnState
    search-query :- SearchQuery
    ident]
   (make-search-query es-conn-state search-query ident {}))
  ([es-conn-state :- ESConnState
    search-query :- SearchQuery
    ident
    {:keys [extra-filters]}]
   (let [{:keys [services]} es-conn-state
         {{:keys [get-in-config]} :ConfigService} services
         {:keys [filter-map range full-text]} search-query
         range-query (when range
                       {:range range})
         filter-terms (-> (ensure-document-id-in-map filter-map)
                          q/prepare-terms)]
     {:bool
      {:filter
       (cond-> [(es.query/find-restriction-query-part ident get-in-config)]
         true (into extra-filters)
         (seq filter-map) (into filter-terms)
         (seq range)      (conj range-query)
         (seq full-text)  (into (es.query/refine-full-text-query-parts
                                  es-conn-state full-text)))}})))

(s/defn handle-query-string-search
  "Generate an ES query handler for given schema schema"
  ([partial-stored-schema]
   (handle-query-string-search partial-stored-schema (read1-map-default partial-stored-schema)))
  ([partial-stored-schema :- (s/protocol s/Schema)
    {:keys [es-partial-stored-schema es-partial-stored->partial-stored]} :- Read1MapArg]
   (let [es-partial-stored->partial-stored (build-stored-transformer es-partial-stored->partial-stored
                                                                     es-partial-stored-schema
                                                                     partial-stored-schema)
         coerce! (list-response-coercer es-partial-stored-schema
                                        es-partial-stored->partial-stored)]
     (s/fn :- (list-response-schema partial-stored-schema)
       [{:keys [props] :as es-conn-state} :- ESConnState
        {:keys [search-query ident] :as query-string-search-args} :- QueryStringSearchArgs]
       (let [{conn :conn, index :index
              {{:keys [get-in-config]} :ConfigService}
              :services}  es-conn-state
             query        (make-search-query es-conn-state search-query ident)
             query-params (make-query-params (-> (select-keys query-string-search-args [:params :sort-extension-definitions])
                                                 (assoc :props props)))]
         (cond-> (coerce! (ductile.doc/query
                            conn
                            index
                            query
                            query-params))
           (restricted-read? ident) (update
                                      :data
                                      access-control-filter-list
                                      ident
                                      get-in-config)))))))

(s/defn handle-delete-search
  "ES delete by query handler"
  [{:keys [conn index] :as es-conn-state} :- ESConnState
   search-query :- SearchQuery
   ident
   es-params]
  (let [query (make-search-query es-conn-state search-query ident)
        opts (select-keys es-params [:wait_for_completion])
        nb-deleted (ductile.doc/count-docs conn index query)]
    (log/info (format "deleting %s documents in %s (%s)"
                      nb-deleted
                      index
                      (pr-str query)))
    (:deleted
     (ductile.doc/delete-by-query conn
                                  [index]
                                  query
                                  opts)
     nb-deleted)))

(s/defn handle-query-string-count :- (s/pred nat-int?)
  "ES count handler"
  [{conn :conn
    index :index
    :as es-conn-state} :- ESConnState
   search-query :- SearchQuery
   ident]
  (let [query (make-search-query es-conn-state search-query ident)]
    (ductile.doc/count-docs conn
                            index
                            query)))

(s/defn make-histogram
  [{:keys [aggregate-on granularity timezone]
    :or {timezone "+00:00"}} :- HistogramQuery]
  {:date_histogram
   {:field aggregate-on
    :interval granularity ;; TODO switch to calendar_interval with ES7
    :time_zone timezone}})

(s/defn make-average
  [{:keys [aggregate-on]} :- AverageQuery]
  {:avg {:field aggregate-on}})

(s/defn make-topn
  [{:keys [aggregate-on limit sort_order]
    :or {limit 10 sort_order :desc}} :- TopnQuery]
  {:terms
   {:field (get enumerable-fields-mapping aggregate-on aggregate-on)
    :size limit
    :order {:_count sort_order}}})

(s/defn make-cardinality
  [{:keys [aggregate-on]} :- CardinalityQuery]
  {:cardinality {:field (get enumerable-fields-mapping aggregate-on aggregate-on)
                 :precision_threshold 10000}})

(s/defn make-aggregation
  [{:keys [agg-type agg-key aggs]
    :or {agg-key :metric}
    :as agg-query} :- AggQuery]
  (let [root-agg (dissoc agg-query :aggs)
        agg-fn
        (case agg-type
          :topn make-topn
          :cardinality make-cardinality
          :histogram make-histogram
          :avg make-average
          (throw (ex-info (str "invalid aggregation type: " (pr-str agg-type))
                          {})))]
    (cond-> {agg-key (agg-fn root-agg)}
      (seq aggs) (assoc :aggs (make-aggregation aggs)))))

(s/defn aggregation-filters :- [(s/pred map?)]
  [{:keys [agg-type aggs]
    :as agg-query} :- AggQuery]
  (cond-> (or (some-> (not-empty aggs) aggregation-filters)
              [])
    (= :avg agg-type) (conj {:bool {:must {:exists {:field (:aggregate-on agg-query)}}}})))

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
                    buckets)
    :avg value))

(s/defn handle-aggregate
  "Generate an ES aggregation handler for given schema"
  [{:keys [conn index] :as es-conn-state} :- ESConnState
   search-query :- SearchQuery
   {:keys [agg-type] :as agg-query} :- AggQuery
   ident]
  (let [agg-query (assoc agg-query :agg-key :metric)
        query (make-search-query es-conn-state search-query ident {:extra-filters (aggregation-filters agg-query)})
        agg (make-aggregation agg-query)
        es-res (ductile.doc/query conn
                                  index
                                  query
                                  agg
                                  {:limit 0})]
    {:data (format-agg-result agg-type
                              (get-in es-res [:aggs :metric]))
     :paging (select-keys (:paging es-res) [:total-hits])}))
