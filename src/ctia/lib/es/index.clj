(ns ctia.lib.es.index
  (:require [clojure.core.memoize :as memo]
            [schema.core :as s]
            [clj-time.core :as t]
            [clojurewerkz.elastisch.native :as n]
            [clojurewerkz.elastisch.rest :as h]
            [clojurewerkz.elastisch.native.index :as native-index]
            [clojurewerkz.elastisch.rest.index :as rest-index]))

(s/defschema ESConn
  (s/either clojurewerkz.elastisch.rest.Connection
            org.elasticsearch.client.transport.TransportClient))

(def alias-create-fifo-threshold 5)

(s/defschema ESConnState
  {:index s/Str
   :props {s/Any s/Any}
   :mapping {s/Any s/Any}
   :conn ESConn})

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

(defn update-alias-fn [conn]
  (if (native-conn? conn)
    native-index/update-aliases
    rest-index/update-aliases))

(defn refresh-fn [conn]
  (if (native-conn? conn)
    native-index/refresh
    rest-index/refresh))

(defn connect [props]
  "instantiate an ES conn from props"
  (if (:uri props)
    (h/connect (:uri props))
    (n/connect [[(:host props) (Integer. (:port props))]]
               {"cluster.name" (:clustername props)})))

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

(defn create-alias!
  "create an index alias simple or filtered"
  ([conn index alias]
   (:acknowledged
    ((update-alias-fn conn)
     conn
     {:add {:index index
            :alias alias}})))
  ([conn index alias routing filter]
   (:acknowledged
    ((update-alias-fn conn)
     conn
     {:add {:index index
            :alias alias
            :routing routing
            :filter filter}}))))

(s/defn create-aliased-index!
  "create an index with an alias for a slice"
  [state :- ESConnState
   index-name :- s/Str]

  (create!
   (:conn state)
   index-name
   (:mapping state))

  (create-alias!
   (:conn state)
   index-name
   (:index state)))

(s/defn create-filtered-alias!
  "create a filtered index alias"
  [state :- ESConnState
   name :- s/Str
   routing :- s/Str
   filter :- {s/Any s/Any}]

  (create!
   (:conn state)
   (:index state)
   (:mapping state))

  (create-alias!
   (:conn state)
   (:index state)
   name
   routing
   filter))

(def memo-create-filtered-alias!
  (memo/fifo create-filtered-alias!
             :fifo/threshold
             alias-create-fifo-threshold))

(def memo-create-aliased-index!
  (memo/fifo create-aliased-index!
             :fifo/threshold
             alias-create-fifo-threshold))

