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
(def es-conn (atom nil))
(def index-name (atom nil))

(defn read-index-spec []
  (let [props (Properties.)]
    (.load props (-> index-properties-file
                     io/resource
                     io/reader))

    (into {} (map (fn [[k v]]
                    [(keyword k) v])
                  props))))

(defn init! []
  (let [props (read-index-spec)]
    (reset! index-name (:indexname props))
    (reset! es-conn
            (n/connect [[(:host props) (Integer. (:port props))]]
                       {"cluster.name" (:clustername props)}))))

(defn delete! [conn]
  (when (idx/exists? conn @index-name)
    (idx/delete conn @index-name)))

(defn create! [conn]
  (when-not (idx/exists? conn @index-name)
    (idx/create conn @index-name :mappings mappings)))
