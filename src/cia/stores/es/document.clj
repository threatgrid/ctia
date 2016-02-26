(ns cia.stores.es.document
  (:require
   [clojurewerkz.elastisch.native.document :as document]
   [clojurewerkz.elastisch.query :as q]
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
   :id (:id doc)
   :refresh true)
  (get-doc conn index-name mapping (:id doc)))

(defn update-doc
  "update a document on es return the updated document"
  [conn index-name mapping id doc]
  (document/update-with-partial-doc @conn index-name mapping id doc)
  (get-doc conn index-name mapping id))

(defn delete-doc
  "delete a document on es and return nil if ok"
  [conn index-name mapping id]
  (:found?
   (document/delete @conn index-name mapping id)))

(defn mk-filter-val [v]
  "lower case any string"
  (if (string? v)
    (clojure.string/lower-case v) v))

(defn mk-nested-filter [n-index]
  (map
   #(hash-map :nested
              {:path (name (key %))
               :query {:bool
                       {:must
                        (map (fn [kv]
                               (q/term (->> (key kv)
                                            (map name)
                                            (clojure.string/join "."))
                                       (val kv))) (mk-filter-val
                                                   (val %)))}}}) n-index))

(defn mk-flat-filter [flat-terms]
  (map #(q/terms (key %)
                 (mk-filter-val [(val %)])) flat-terms))

(defn filter-map->terms-query [filter-map]
  "transforms a filter map to en ES terms query
   only supports one level of nesting"
  (let [flat-terms (into {}
                         (filter #(keyword? (first %)) filter-map))
        nested-terms (into {}
                           (filter #(vector? (first %)) filter-map))
        n-index (group-by #(ffirst %) nested-terms)
        nested-fmt (mk-nested-filter n-index)
        flat-fmt (mk-flat-filter flat-terms)]

    {:filtered
     {:query {:match_all {}}
      :filter {:bool
               {:must (concat nested-fmt
                              flat-fmt)}}}}))

(defn search-docs
  "search for documents on es, return only the docs"
  [conn index-name mapping filter-map]
  (let [filters (filter-map->terms-query filter-map)
        res (document/search @conn index-name mapping :query filters)]
    (->> res
         hits-from
         (map :_source))))
