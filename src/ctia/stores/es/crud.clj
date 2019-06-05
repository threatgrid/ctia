(ns ctia.stores.es.crud
  (:require [clojure.set :as set]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]
            [schema-tools.core :as st]
            [clj-momo.lib.es
             [document :as d]
             [query :as q]
             [schemas :refer [ESConnState]]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.properties :refer [properties]]
            [ctia.domain.access-control
             :refer [acl-fields allow-read? allow-write?]]
            [ctia.stores.es.query
             :refer [find-restriction-query-part]]))

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
  (let [[orig docid] (re-matches #".*?([^/]+)\z" id) ]
    docid))

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
  (let [{{:keys [errors items]}
         :es-http-res-body} exception-data]
    {:data (map (fn [{:keys [error _id] :as item} model]
                  (if error
                    {:error error
                     :id _id}
                    (build-create-result model coerce-fn)))
                (remove-es-actions items) models)}))

(s/defn get-doc-with-index :- (s/maybe s/Str)
  "given an indexname which could be an alias pointing to multiple indices,
  it returns the real index in which is the document with given _id,
  if it exists, nil otherwise"
  [state :- ESConnState
   mapping :- s/Keyword
   _id :- s/Str
   params]
  (let [ids-query (q/ids [(ensure-document-id _id)])
        res (d/query (:conn state)
                     (:index state)
                     (name mapping)
                     ids-query
                     (make-es-read-params params)
                     true)]
    (-> res :data first)))

(defn handle-create
  "Generate an ES create handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe [Model])
      [state :- ESConnState
       models :- [Model]
       ident
       {:keys [refresh] :as params}]
      (try
        (map #(build-create-result % coerce!)
             (d/bulk-create-doc (:conn state)
                                (map #(assoc %
                                             :_id (:id %)
                                             :_index (-> state :props :write-alias)
                                             :_type (name mapping))
                                     models)
                                (or refresh
                                    (get-in state [:props :refresh] false))))
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
       ident]
      (when-let [{index :_index current-doc :_source}
                 (get-doc-with-index state mapping id {})]
        (if (allow-write? current-doc ident)
          (coerce! (d/update-doc (:conn state)
                                 index
                                 (name mapping)
                                 (ensure-document-id id)
                                 realized
                                 (get-in state [:props :refresh] false)))
          (throw (ex-info "You are not allowed to update this document"
                          {:type :access-control-error})))))))

(defn handle-read
  "Generate an ES read handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str
       ident
       params]
      (when-let [doc (-> (get-doc-with-index state
                                             mapping
                                             id
                                             (make-es-read-params params))
                         :_source
                         coerce!)]
        (if (allow-read? doc ident)
          doc
          (throw (ex-info "You are not allowed to read this document"
                          {:type :access-control-error})))))))

(defn access-control-filter-list
  "Given an ident, keep only documents it is allowed to read"
  [docs ident]
  (filter #(allow-read? % ident) docs))

(defn handle-delete
  "Generate an ES delete handler using some mapping and schema"
  [mapping Model]
  (s/fn :- s/Bool
    [state :- ESConnState
     id :- s/Str
     ident]
    (when-let [{index :_index doc :_source}
               (get-doc-with-index state mapping id {})]
        (if (allow-write? doc ident)
          (d/delete-doc (:conn state)
                        index
                        (name mapping)
                        (ensure-document-id id)
                        (get-in state [:props :refresh] false))

          (throw (ex-info "You are not allowed to delete this document"
                          {:type :access-control-error}))))))

(def default-sort-field :_doc)

(defn with-default-sort-field
  [params]
  (if (contains? params :sort_by)
    params
    (assoc params :sort_by default-sort-field)))


(s/defschema FilterSchema
  (st/optional-keys
   {:all-of {s/Any s/Any}
    :one-of {s/Any s/Any}}))

(defn handle-find
  "Generate an ES find/list handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [state :- ESConnState
       filters :- FilterSchema
       ident
       params]
      (let [{:keys [all-of one-of]
             :or {all-of {} one-of {}}} filters
            filter-val (conj (q/prepare-terms all-of)
                             (find-restriction-query-part ident))
            bool-params (merge {:filter filter-val}
                               (when (not-empty one-of)
                                 {:should (q/prepare-terms one-of)
                                  :minimum_should_match 1}))]
        (update
         (coerce! (d/query (:conn state)
                           (:index state)
                           (name mapping)
                           (q/bool bool-params)
                           (-> params
                               with-default-sort-field
                               make-es-read-params)))
         :data access-control-filter-list ident)))))

(defn handle-query-string-search
  "Generate an ES query string handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [{conn :conn
        index :index
        {:keys [default_operator]} :props} :- ESConnState
       query :- s/Str
       filter-map :- (s/maybe {s/Any s/Any})
       ident
       params]
      (let [query_string (into {:query query}
                               (when default_operator
                                 {:default_operator default_operator}))]
        (update
         (coerce! (d/search-docs conn
                                 index
                                 (name mapping)
                                 {:bool {:must [(find-restriction-query-part ident)
                                                {:query_string query_string}]}}
                                 filter-map
                                 (-> params
                                     with-default-sort-field
                                     make-es-read-params)))
         :data access-control-filter-list ident)))))
