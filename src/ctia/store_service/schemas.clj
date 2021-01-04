(ns ctia.store-service.schemas
  (:require [ctia.ductile-service.schemas :as ductile]
            [ctia.schemas.utils :as csu]
            [schema.core :as s]))

(s/defschema Services
  {:ConfigService {:get-in-config (s/=>* s/Any
                                         [(s/named [s/Any] 'path)]
                                         [(s/named [s/Any] 'path)
                                          (s/named s/Any 'default)])}
   :DuctileService (-> ductile/ServiceGraph
                       (csu/select-all-keys [:request-fn]))})

(s/defschema StoreID
  "An identifier for a store."
  (s/pred simple-keyword?))

(s/defschema Store
  (s/pred map?))

(s/defschema GetStoreFn
  "ctia.store-service/get-store in the service graph."
  (s/=> Store
        (s/named StoreID 'store-id)))

(s/defschema Stores
  "A map of stores with various backends indexed by their identifiers.
  Currently, the vals of this map are always length 1: the ES store."
  {StoreID [Store]})

(s/defschema AllStoresFn
  "ctia.store-service/all-stores in the service graph."
  (s/=> Stores))

(s/defschema StoresAtom
  "An atom containing a sequence of stores."
  (s/atom Stores))

(s/defschema StoreServiceCtx
  "The service-context for StoreService."
  {:services Services
   :stores-atom StoresAtom})
