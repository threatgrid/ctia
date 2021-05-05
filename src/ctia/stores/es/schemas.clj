(ns ctia.stores.es.schemas
  (:require [ctia.schemas.services :as external-svc-fns]
            [ctia.schemas.utils :as csu]
            [ductile.schemas] ;; no alias to avoid ESConnState clashes
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnServices
  {:ConfigService (csu/select-all-keys
                    external-svc-fns/ConfigServiceFns
                    #{:get-in-config})})

(s/defschema ESConnState
  (st/merge
    ;; disallows services
    ductile.schemas/ESConnState
    {:services ESConnServices}))
