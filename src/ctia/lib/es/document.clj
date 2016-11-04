(ns ctia.lib.es.document
  (:require
   [clojure.tools.logging :as log]
   [ctia.lib.pagination :as pagination]
   [ctia.lib.es.query :refer [filter-map->terms-query]]
   [clojurewerkz.elastisch.native.bulk :as native-bulk]
   [clojurewerkz.elastisch.rest.bulk :as rest-bulk]
   [clojurewerkz.elastisch.native.document :as native-document]
   [clojurewerkz.elastisch.rest.document :as rest-document]
   [clojurewerkz.elastisch.native.response :as native-response]
   [clojurewerkz.elastisch.rest.response :as rest-response]))

(def default-limit 1000)

(defn native-conn? [conn]
  (not (:uri conn)))

(defn get-doc-fn [conn]
  (if (native-conn? conn)
    native-document/get
    rest-document/get))

(defn bulk-index-fn [conn]
  (if (native-conn? conn)
    native-bulk/bulk-index
    rest-bulk/bulk-index))

(defn bulk-fn [conn]
  (if (native-conn? conn)
    native-bulk/bulk
    rest-bulk/bulk))

(defn create-doc-fn [conn]
  (if (native-conn? conn)
    native-document/create
    rest-document/create))

(defn update-doc-fn [conn]
  (if (native-conn? conn)
    native-document/update-with-partial-doc
    rest-document/update-with-partial-doc))

(defn delete-doc-fn [conn]
  (if (native-conn? conn)
    native-document/delete
    rest-document/delete))

(defn search-doc-fn [conn]
  (if (native-conn? conn)
    native-document/search
    rest-document/search))

(defn hits-from-fn [conn]
  (if (native-conn? conn)
    native-response/hits-from
    rest-response/hits-from))

(defn get-doc
  "get a document on es and return only the source"
  [conn index-name mapping id]

  (:_source ((get-doc-fn conn)
             conn
             index-name
             mapping
             id)))

(defn create-doc
  "create a document on es return the created document"
  [conn index-name mapping doc refresh?]
  ((create-doc-fn conn)
   conn
   index-name
   mapping
   doc
   {:id (:id doc)
    :refresh refresh?})
  doc)

(defn bulk-create-doc
  "create multiple documents on ES and return the created documents"
  [conn docs refresh?]
  (let [bulk-index (bulk-index-fn conn)
        bulk (bulk-fn conn)
        index-operations (bulk-index docs)]
    (bulk conn
          index-operations
          {:refresh refresh?}))
  docs)

(defn update-doc
  "update a document on es return the updated document"
  [conn index-name mapping id doc refresh?]

  (let [res ((update-doc-fn conn)
             conn
             index-name
             mapping
             id
             doc
             {:refresh refresh?
              :fields "_source"})]
    (or (get-in res [:get-result :source])
        (get-in res [:get :_source]))))

(defn delete-doc
  "delete a document on es and return nil if ok"
  [conn index-name mapping id refresh?]

  (:found
   ((delete-doc-fn conn)
    conn
    index-name
    mapping
    id
    {:refresh refresh?})))

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

(defn search-docs
  "Search for documents on es using a query string search.  Also applies a filter map, converting the values in the filter-map into must match terms."
  [conn index-name mapping query filter-map params]
  (let [ es-params (generate-es-params query filter-map params)
        res (->> ((search-doc-fn conn)
                  conn
                  index-name
                  mapping
                  es-params))
        hits (get-in res [:hits :total] 0)
        results (->> res
                     ((hits-from-fn conn))
                     (map :_source))]
    
    (log/debug "search-docs: " es-params )
    
    (pagination/response (or results [])
                         (:from es-params)
                         (:size es-params)
                         hits)))
