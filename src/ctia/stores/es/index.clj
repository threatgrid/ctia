(ns ctia.stores.es.index
  (:require
   [clojurewerkz.elastisch.native :as n]
   [clojurewerkz.elastisch.native.index :as idx]
   [ctia.stores.es.mapping :refer [mappings]]
   [ctia.properties :refer [properties]]))

(defn read-index-spec []
  "read es index config properties, returns an option map"
  (get-in @properties [:ctia :store :es]))

(defn init-conn []
  "initiate an ES connection returns a map containing transport and
   the configured index name"

  (let [props (read-index-spec)]
    {:index (:indexname props)
     :conn (n/connect [[(:host props) (Integer. (:port props))]]
                      {"cluster.name" (:clustername props)})}))

(defn delete!
  "delete an index, abort if non existant"
  [conn index-name]
  (when (idx/exists? conn index-name)
    (idx/delete conn index-name)))

(defn create!
  "create an index, abort if already exists"
  [conn index-name]
  (when-not (idx/exists? conn index-name)
    (idx/create conn index-name :mappings mappings)))
