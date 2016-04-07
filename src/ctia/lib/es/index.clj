(ns ctia.lib.es.index
  (:require
   [schema.core :as s]
   [clojurewerkz.elastisch.native :as n]
   [clojurewerkz.elastisch.rest :as h]
   [clojurewerkz.elastisch.native.index :as native-index]
   [clojurewerkz.elastisch.rest.index :as rest-index]
   [ctia.stores.es.mapping :refer [store-mappings]]
   [ctia.events.producers.es.mapping :refer [producer-mappings]]
   [ctia.properties :refer [properties]]))

(s/defschema ESConnState
  {:index s/Str
   :mapping {s/Any s/Any}
   :conn (s/either
          clojurewerkz.elastisch.rest.Connection
          org.elasticsearch.client.transport.TransportClient)})

(defn native-conn? [conn]
  (not (:uri conn)))

(defn index-exists?-fn [conn]
  (if (native-conn? conn)
    native-index/exists?
    rest-index/exists?))

(defn index-create-fn [conn]
  (if (native-conn? conn)
    native-index/create
    rest-index/create))

(defn index-delete-fn [conn]
  (if (native-conn? conn)
    native-index/delete
    rest-index/delete))

(defn read-store-index-spec []
  "read es store index config properties, returns an option map"
  (get-in @properties [:ctia :store :es]))

(defn read-producer-index-spec []
  "read es producer index config properties, returns an option map"
  (get-in @properties [:ctia :producer :es]))

(defn connect [props]
  "instantiate an ES conn from props"
  (if (:uri props)
    (h/connect (:uri props))
    (n/connect [[(:host props) (Integer. (:port props))]]
               {"cluster.name" (:clustername props)})))

(s/defn init-store-conn :- ESConnState []
  "initiate an ES store connection returns a map containing transport,
   mapping, and the configured index name"
  (let [props (read-store-index-spec)]
    {:index (:indexname props)
     :mapping store-mappings
     :conn (connect props)}))

(s/defn init-producer-conn :- ESConnState []
  "initiate an ES producer connection returns a map containing transport,
   mapping and the configured index name"
  (let [props (read-producer-index-spec)]
    {:index (:indexname props)
     :mapping producer-mappings
     :conn (connect props)}))

(defn delete!
  "delete an index, abort if non existant"
  [conn index-name]
  (when ((index-exists?-fn conn) conn index-name)
    ((index-delete-fn conn) conn index-name)))

(defn create!
  "create an index, abort if already exists"
  [conn index-name mappings]
  (when-not ((index-exists?-fn conn) conn index-name)
    ((index-create-fn conn) conn index-name :mappings mappings)))
