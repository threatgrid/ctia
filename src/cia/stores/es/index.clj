(ns cia.stores.es.index
  (:require
   [clojure.java.io :as io]
   [clojure.string :refer [split]]
   [schema.core :as s]
   [schema.coerce :as coerce]
   [clojurewerkz.elastisch.native :as n]
   [clojurewerkz.elastisch.native.index :as idx]
   [cia.stores.es.mapping :refer [mappings]])
  (:import java.util.Properties))

(def index-properties-file "es-index.properties")

(defn read-index-spec []
  "read es index config properties, returns an option map"
  (let [props (Properties.)]
    (.load props (-> index-properties-file
                     io/resource
                     io/reader))

    (into {} (map (fn [[k v]]
                    [(keyword k) v])
                  props))))

(defn init-conn []
  "initiate an ES connection returns a map containing transport and
   the configured index name"

  (let [props (read-index-spec)]
    {:index (:indexname props)
     :conn (n/connect [[(:host props) (Integer. (:port props))]]
                      {"cluster.name" (:clustername props)})}))

(defn delete! [conn index-name]
  "delete an index, abort if non existant"
  (when (idx/exists? conn index-name)
    (idx/delete conn index-name)))

(defn create! [conn index-name]
  "create an index, abort if already exists"
  (when-not (idx/exists? conn index-name)
    (idx/create conn index-name :mappings mappings)))
