(ns ctia.lib.es.index
  (:require [clj-time.core :as t]
            [clojure.core.memoize :as memo]
            [clojurewerkz.elastisch.native :as n]
            [clojurewerkz.elastisch.rest :as h]
            [clojurewerkz.elastisch.native.index :as native-index]
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
   :mapping {s/Any s/Any}
   :conn ESConn
   (s/optional-key :slicing) ESSlicing})

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

(defn create!
  "create an index, abort if already exists"
  [conn index-name mappings]
  (when-not ((index-exists?-fn conn) conn index-name)
    ((index-create-fn conn) conn index-name {:mappings mappings})))

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
  [{:keys [conn index mapping] :as state :- ESConnState}
   index-name :- s/Str]

  (create! conn index-name mapping)
  (create-alias! conn index-name index))

(s/defn create-filtered-alias!
  "create a filtered index alias"
  [{:keys [conn index mapping] :as state :- ESConnState}
   name :- s/Str
   routing :- s/Str
   filter :- {s/Any s/Any}]

  (create! conn index mapping)
  (create-alias! conn index name routing filter))

(def memo-create-filtered-alias!
  (memo/fifo create-filtered-alias!
             :fifo/threshold
             alias-create-fifo-threshold))

(def memo-create-aliased-index!
  (memo/fifo create-aliased-index!
             :fifo/threshold
             alias-create-fifo-threshold))
