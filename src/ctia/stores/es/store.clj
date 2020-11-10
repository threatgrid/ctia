(ns ctia.stores.es.store
  (:require [schema.core :as s]
            [schema-tools.core :as st]
            [ductile
             [index :as es-index]
             [schemas :refer [ESConn]]]
            [ctia.store :refer [IStore IQueryStringSearchableStore]]
            [ctia.stores.es.crud :as crud]))

(defn delete-state-indexes [{:keys [conn index config] :as state}]
  (when conn
    (es-index/delete! conn (str index "*"))))

(defmacro def-es-store
  [store-name
   entity
   stored-schema
   partial-stored-schema]
  (assert (symbol? store-name) (pr-str store-name))
  (assert (keyword? entity) (pr-str entity))
  (assert (symbol? stored-schema) (pr-str stored-schema))
  (assert (not= 'state stored-schema) "Captured stored-schema binding!")
  (assert (not= 'state partial-stored-schema) "Captured partial-stored-schema binding!")
  `(defrecord ~store-name [~(symbol "state")]
     IStore
     (~(symbol "read-record") [_# id# ident# params#]
      ((crud/handle-read ~partial-stored-schema)
       ~(symbol "state")  id# ident# params#))
     (~(symbol "create-record") [_# new-actors# ident# params#]
      ((crud/handle-create ~entity ~stored-schema)
       ~(symbol "state") new-actors# ident# params#))
     (~(symbol "update-record") [_# id# actor# ident# params#]
      ((crud/handle-update ~entity ~stored-schema)
       ~(symbol "state") id# actor# ident# params#))
     (~(symbol "delete-record") [_# id# ident# params#]
      ((crud/handle-delete ~entity)
       ~(symbol "state") id# ident# params#))
     (~(symbol "list-records") [_# filter-map# ident# params#]
      ((crud/handle-find ~partial-stored-schema)
       ~(symbol "state") filter-map# ident# params#))
     IQueryStringSearchableStore
     (~(symbol "query-string-search") [_# search-query# ident# params#]
      ((crud/handle-query-string-search ~partial-stored-schema)
       ~(symbol "state") search-query# ident# params#))
     (~(symbol "query-string-count") [_# search-query# ident#]
      (crud/handle-query-string-count
       ~(symbol "state") search-query# ident#))
     (~(symbol "aggregate") [_# search-query# agg-query# ident#]
      (crud/handle-aggregate
       ~(symbol "state") search-query# agg-query# ident#))))

(s/defschema StoreMap
  {:conn ESConn
   :indexname s/Str
   :mapping s/Str
   :type s/Str
   :settings s/Any
   :config s/Any
   :props {s/Any s/Any}})


(s/defn store-state->map :- StoreMap
  "transform a store state
   into a properties map for easier manipulation,
   override the cm to use the custom timeout "
  [{:keys [index props conn config] :as state}
   conn-overrides]
  (let [entity-type (-> props :entity name)]
    {:conn (merge conn conn-overrides)
     :indexname index
     :props props
     :mapping entity-type
     :type entity-type
     :settings (:settings props)
     :config config}))

(s/defn store->map :- StoreMap
  "transform a store record
   into a properties map for easier manipulation,
   override the cm to use the custom timeout "
  [store conn-overrides]
  (-> store first :state
      (store-state->map conn-overrides)))
