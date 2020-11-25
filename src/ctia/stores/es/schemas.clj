(ns ctia.stores.es.schemas
  (:require [ctia.store-service.schemas :as store-schemas]
            [ductile.schemas] ;; no alias to avoid ESConnState clashes
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnServices
  store-schemas/Services)

(s/defschema ESConnState
  (st/merge
    ;; disallows services
    ductile.schemas/ESConnState
    {:services ESConnServices}))
