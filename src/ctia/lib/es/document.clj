(ns ctia.lib.es.document
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia.lib.es
             [index :refer [ESConn]]
             [query :refer [filter-map->terms-query]]]
            [ctia.lib.pagination :as pagination]
            [schema.core :as s]))

(def default-limit 1000)

(s/defschema Refresh
  (s/enum true false "wait_for"))

(defn create-doc-uri [uri index-name mapping id]
  (format "%s/%s/%s/%s" uri index-name mapping id))

(def delete-doc-uri create-doc-uri)
(def get-doc-uri create-doc-uri)

(defn update-doc-uri [uri index-name mapping id]
  (format "%s/%s/%s/%s/_update" uri index-name mapping id))

(defn bulk-uri [uri]
  (format "%s/_bulk" uri))

(defn search-uri [uri index-name mapping]
  (format "%s/%s/%s/_search" uri index-name mapping))

(def ^:private special-operation-keys
  [:_doc_as_upsert
   :_index
   :_type
   :_id
   :_retry_on_conflict
   :_routing
   :_percolate
   :_parent
   :_script
   :_script_params
   :_scripted_upsert
   :_timestamp
   :_ttl
   :_version
   :_version_type])

(defn index-operation [doc]
  {"index" (select-keys doc special-operation-keys)})

(defn bulk-index
  "generates the content for a bulk insert operation"
  ([documents]
   (let [operations (map index-operation documents)
         documents  (map #(apply dissoc % special-operation-keys) documents)]
     (interleave operations documents))))

(s/defn get-doc
  "get a document on es and return only the source"
  [{:keys [uri cm]} :- ESConn index-name mapping id]
  (-> (client/get (get-doc-uri uri index-name mapping id)
                  {:throw-exceptions false
                   :connection-manager cm
                   :as :json})
      :body
      :_source))

(s/defn create-doc
  "create a document on es return the created document"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   mapping :- s/Str
   {:keys [id] :as doc} :- s/Any
   refresh? :- Refresh]

  (client/put (create-doc-uri uri index-name mapping id)
              {:form-params doc
               :query-params
               {:refresh refresh?}
               :connection-manager cm
               :content-type :json
               :as :json})
  doc)

(s/defn bulk-create-doc
  "create multiple documents on ES and return the created documents"
  [{:keys [uri cm]} :- ESConn
   docs :- [s/Any]
   refresh? :- Refresh]

  (let [ops (bulk-index docs)
        json-ops (map #(json/generate-string % {:pretty false}) ops)
        bulk-body (-> json-ops
                      (interleave (repeat "\n"))
                      string/join)]
    (client/post (bulk-uri uri)
                 {:query-params {:refresh refresh?}
                  :body bulk-body}))
  docs)

(s/defn update-doc
  "update a document on es return the updated document"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   mapping :- s/Str
   id :- s/Str
   doc :- s/Any
   refresh? :- Refresh]

  (client/post (update-doc-uri uri index-name mapping id)
               {:form-params {:doc doc}
                :query-params {:refresh refresh?}
                :connection-manager cm
                :content-type :json
                :as :json})
  doc)

(s/defn delete-doc
  "delete a document on es and return nil if ok"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   mapping :- s/Str
   id :- s/Str
   refresh? :- Refresh]

  (-> (client/delete (delete-doc-uri uri index-name mapping id)
                     {:query-params {:refresh refresh?}
                      :connection-manager cm
                      :as :json})
      :body
      :found))

(defn params->pagination
  [{:keys [sort_by sort_order offset limit]
    :or {sort_by :id
         sort_order :asc
         offset 0
         limit pagination/default-limit}}]
  (merge
   {}
   (when sort_by
     {:sort {sort_by sort_order}})
   (when limit
     {:size limit})
   (when offset
     {:from offset})))


(defn generate-es-params [query filter-map params]
  (let [query-map (filter-map->terms-query filter-map query)]
    (merge (params->pagination params)
           {:query query-map}
           (select-keys params [:sort]))))

(s/defn search-docs
  "Search for documents on es using a query string search.  Also applies a filter map, converting
   the values in the filter-map into must match terms."

  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   mapping :- s/Str
   query :- s/Any
   filter-map :- s/Any
   params :- s/Any]

  (let [es-params (generate-es-params query filter-map params)
        res (:body (client/post
                    (search-uri uri index-name mapping)
                    {:form-params es-params
                     :connection-manager cm
                     :content-type :json
                     :as :json}))
        hits (get-in res [:hits :total] 0)
        results (->> res :hits :hits (map :_source))]

    (log/debug "search-docs:" es-params)

    (pagination/response (or results [])
                         (:from es-params)
                         (:size es-params)
                         hits)))
