(ns ctia.store-service.schemas
  (:require [schema.core :as s]))

(s/defschema StoreID
  "An identifier for a store."
  (s/pred simple-keyword?))

(s/defschema Store
  (s/pred map?))

(s/defschema WriteStoreFnOptions
  ":args  Extra args to provide to write-fn after the store."
  {:args [s/Any]})

(s/defschema ReadStoreFn
  "ctia.store-service/read-store in the service graph."
  (s/=> Store
        (s/named StoreID 'store-id)))

(s/defschema WriteStoreFn
  "ctia.store-service/write-store in the service graph."
  (s/=>* Store
         [(s/named StoreID 'store-id)
          (s/named (s/=> Store Store) 'write-fn)]
         [(s/named StoreID 'store-id)
          ;; varargs: (s/=> Store Store Args*)
          (s/named ifn? 'write-fn)
          (s/named WriteStoreFnOptions 'opt)]))

(s/defschema Stores
  "A map of stores with various backends indexed by their identifiers.
  Currently, the the vals of this map are always length 1: the ES store."
  {StoreID [Store]})

(s/defschema AllStoresFn
  "ctia.store-service/all-stores in the service graph."
  (s/=> Stores))

(s/defschema StoresAtom
  "An atom containing a sequence of stores."
  (s/atom Stores))

(s/defschema StoreServiceCtx
  "The service-context for StoreService."
  {:stores-atom StoresAtom})
