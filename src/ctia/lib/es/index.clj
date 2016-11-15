(ns ctia.lib.es.index
  (:require [clojure.core.memoize :as memo]
            [clojurewerkz.elastisch.native :as n]
            [clojurewerkz.elastisch.native.index :as native-index]
            [clojurewerkz.elastisch.rest :as h]
            [clojurewerkz.elastisch.rest.index :as rest-index]
            [schema.core :as s]))

(s/defschema ESConn
  (s/either clojurewerkz.elastisch.rest.Connection
            org.elasticsearch.client.transport.TransportClient))

(def alias-create-fifo-threshold 5)

(s/defschema ESSlicing
  {:strategy s/Keyword
   :granularity s/Keyword})

(s/defschema ESConnState
  {:index s/Str
   :props {s/Any s/Any}
   :config {s/Any s/Any}
   :conn ESConn
   (s/optional-key :slicing) ESSlicing})

(defn native-conn? [conn]
  (not (:uri conn)))

(defn create-template-fn [conn]
  (if (native-conn? conn)
    native-index/create-template
    rest-index/create-template))

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

(defn update-alias-fn [conn]
  (if (native-conn? conn)
    native-index/update-aliases
    rest-index/update-aliases))

(defn refresh-fn [conn]
  (if (native-conn? conn)
    native-index/refresh
    rest-index/refresh))

(defn connect
  "instantiate an ES conn from props"
  [{:keys [transport host port clustername]
    :or {transport :http}}]
  (case transport
    :native (n/connect [[host port]]
                       {"cluster.name" clustername})
    :http (h/connect  (str "http://" host ":" port))))

(defn delete!
  "delete an index, abort if non existant"
  [conn index-name]
  (when ((index-exists?-fn conn) conn index-name)
    ((index-delete-fn conn) conn index-name)))

(defn create-template!
  "create an index template, update if already exists"
  [conn index-name index-config]

  (let [template (str index-name "*")
        opts (assoc index-config :template template)]
    ((create-template-fn conn) conn index-name opts)))
