(ns ctia.stores.es.store
  (:require [clj-momo.lib.es.index :as es-index]
            [clj-momo.lib.es.conn :as conn]
            [ctia.store :refer :all]
            [ctia.stores.es.crud :as crud]))

(defn delete-state-indexes [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn (str index "*"))))

(defmacro def-es-store
  [store-name
   entity
   stored-schema
   partial-stored-schema]
  `(defrecord ~store-name [~(symbol "state")]
     IStore
     (~(symbol "read-record") [_# id# ident# params#]
      ((crud/handle-read ~entity ~partial-stored-schema) ~(symbol "state")  id# ident# params#))
     (~(symbol "create-record") [_# new-actors# ident# params#]
      ((crud/handle-create ~entity ~stored-schema) ~(symbol "state") new-actors# ident# params#))
     (~(symbol "update-record") [_# id# actor# ident#]
      ((crud/handle-update ~entity ~stored-schema) ~(symbol "state") id# actor# ident#))
     (~(symbol "delete-record") [_# id# ident#]
      ((crud/handle-delete ~entity ~stored-schema) ~(symbol "state") id# ident#))
     (~(symbol "list-records") [_# filter-map# should-map# ident# params#]
      ((crud/handle-find ~entity ~partial-stored-schema) ~(symbol "state") filter-map# should-map# ident# params#))
     IQueryStringSearchableStore
     (~(symbol "query-string-search") [_# query# filtermap# ident# params#]
      ((crud/handle-query-string-search ~entity
                                        ~partial-stored-schema) ~(symbol "state") query# filtermap# ident# params#))))

(defn store->map
  "transform a store record
   into a properties map for easier manipulation,
   override the cm to use the custom timeout "
  [store conn-overrides]
  (let [store-state (-> store first :state)
        entity-type (-> store-state :props :entity name)]
    {:conn (merge (:conn store-state)
                  conn-overrides)
     :indexname (:index store-state)
     :mapping entity-type
     :type entity-type
     :settings (-> store-state :props :settings)
     :config (:config store-state)}))
