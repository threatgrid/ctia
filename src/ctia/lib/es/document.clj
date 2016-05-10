(ns ctia.lib.es.document
  (:require
   [ctia.lib.pagination :as pagination]
   [ctia.lib.es.query :refer [filter-map->terms-query]]
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
  [conn index-name mapping doc]
  ((create-doc-fn conn)
   conn
   index-name
   mapping
   doc
   :id (:id doc)
   :refresh true)
  doc)

(defn update-doc
  "update a document on es return the updated document"
  [conn index-name mapping id doc]

  (let [res ((update-doc-fn conn)
             conn
             index-name
             mapping
             id
             doc
             {:refresh true
              :fields "_source"})]
    (or (get-in res [:get-result :source])
        (get-in res [:get :_source]))))

(defn delete-doc
  "delete a document on es and return nil if ok"
  [conn index-name mapping id]

  (:found
   ((delete-doc-fn conn)
    conn
    index-name
    mapping
    id
    :refresh true)))

(defn params->pagination
  [{:keys [sort_by sort_order offset limit]
    :or {sort_by :id
         sort_order :asc
         offset 0
         limit pagination/default-limit}}]
  (merge
   {}
   (when sort_by
     {:sort [{sort_by sort_order}]})
   (when limit
     {:size limit})
   (when offset
     {:from offset})))

(defn search-docs
  "search for documents on es"
  [conn index-name mapping filter-map params]

  (let [filters (filter-map->terms-query filter-map)
        es-params (merge (params->pagination params)
                         (when filter-map
                           {:query (filter-map->terms-query filter-map)})
                         (select-keys params [:query :sort]))
        res (->> ((search-doc-fn conn)
                  conn
                  index-name
                  mapping
                  es-params))
        hits (get-in res [:hits :total] 0)
        results (->> res
                     ((hits-from-fn conn))
                     (map :_source))]

    (pagination/response (or results [])
                         (:from es-params)
                         (:size es-params)
                         hits)))
