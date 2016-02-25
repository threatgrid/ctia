(ns cia.stores.es.document
  (:require
   [clojurewerkz.elastisch.native.document :as document]
   [clojurewerkz.elastisch.native.response :refer :all]))

(defn get-doc
  "get a document on es and return only the source"
  [conn index-name mapping id]
  (-> (document/get @conn index-name mapping id)
      :source))

(defn create-doc
  "create a document on es return the created document"
  [conn index-name mapping doc]
  (document/create
   @conn
   index-name
   mapping
   doc
   :id (:id doc))

  (get-doc conn index-name mapping (:id doc)))

(defn delete-doc
  "delete a document on es and return nil if ok"
  [conn index-name mapping id]
  (document/delete @conn index-name mapping id)
  nil)

(defn mk-filter-val [v]
  "lower case any string"
  (if (string? v)
    (clojure.string/lower-case v) v))

(defn filter-map->terms-query [filter-map]
  "transforms a filter map to en ES terms query"
  (let [terms (map #(hash-map
                     :term
                     {(keyword (key %))
                      (mk-filter-val (val %))}) filter-map)]

    {:filtered {:query {:match_all {}}
                :filter {:bool {:must terms}}}}))

(defn search-docs
  "search for documents on es, return only the docs"
  [conn index-name mapping filter-map]

  (->> (document/search @conn index-name mapping
                        :query (filter-map->terms-query filter-map))
       hits-from
       (map :_source)))
