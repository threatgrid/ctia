(ns ctia.stores.es.schemas
  (:require [ductile.schemas] ;; no alias to avoid ESConnState clashes
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnServices
  {:ConfigService {:get-in-config (s/=>* s/Any
                                         [(s/named [s/Any] 'path)]
                                         [(s/named [s/Any] 'path)
                                          (s/named s/Any 'default)])}})

(s/defschema ESConnState
  (st/merge
    ;; disallows services
    ductile.schemas/ESConnState
    {:services ESConnServices}))
