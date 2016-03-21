(ns ctia.stores.es.document
  (:require
   [clojure.string :as str]
   [ctia.stores.es.query :refer :all]
   [clojurewerkz.elastisch.native.document :as document]
   [clojurewerkz.elastisch.native.response :refer :all]))

(defn get-doc
  "get a document on es and return only the source"
  [conn index-name mapping id]
  (-> (document/get conn
                    index-name
                    mapping
                    id)
      :source))

(defn create-doc
  "create a document on es return the created document"
  [conn index-name mapping doc]

  (document/create conn
                   index-name
                   mapping
                   doc
                   :id (:id doc)
                   :refresh true)
  doc)

(defn update-doc
  "update a document on es return the updated document"
  [conn index-name mapping id doc]

  (get-in (document/update-with-partial-doc conn
                                            index-name
                                            mapping
                                            id
                                            doc
                                            {:refresh true
                                             :fields "_source"})
          [:get-result :source]))

(defn delete-doc
  "delete a document on es and return nil if ok"
  [conn index-name mapping id]
  (:found?
   (document/delete conn
                    index-name
                    mapping
                    id)))

(defn raw-search-docs [conn index-name mapping query sort]
  (->> (document/search conn
                        index-name
                        mapping
                        :query query
                        :sort sort)
       hits-from
       (map :_source)))

(defn search-docs
  "search for documents on es, return only the docs"
  [conn index-name mapping filter-map]

  (let [filters (filter-map->terms-query filter-map)
        res (document/search conn
                             index-name
                             mapping
                             :query filters)]
    (->> res
         hits-from
         (map :_source))))
