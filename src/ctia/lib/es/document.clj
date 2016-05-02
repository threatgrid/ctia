(ns ctia.lib.es.document
  (:require
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

(defn raw-search-docs [conn index-name mapping query sort]
  (->> ((search-doc-fn conn)
        conn
        index-name
        mapping
        :query query
        :sort sort
        :size default-limit)
       ((hits-from-fn conn))
       (map :_source)))

(defn search-docs
  "search for documents on es, return only the docs"
  [conn index-name mapping filter-map]
  (let [filters (filter-map->terms-query filter-map)
        res ((search-doc-fn conn)
             conn
             index-name
             mapping
             :query filters
             :size default-limit)]
    (->> res
         ((hits-from-fn conn))
         (map :_source))))
