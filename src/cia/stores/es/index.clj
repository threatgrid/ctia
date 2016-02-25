(ns cia.stores.es.index
  (:require
   [clojurewerkz.elastisch.native :as n]
   [clojurewerkz.elastisch.native.index :as idx]
   [cia.stores.es.mapping :refer [mappings]]))


(def es-conn (atom nil))
(def cluster-name "cia_dev")
(def index-name "cia-dev")


(defn setup-conn! []
  (reset! es-conn
          (n/connect [["127.0.0.1" 9300]]
                     {"cluster.name" cluster-name})))

(defn setup-index! []
  (when (idx/exists? @es-conn "cia_dev")
    (idx/delete @es-conn "cia_dev"))
  (idx/create @es-conn "cia_dev" :mappings mappings))

(setup-conn!)



