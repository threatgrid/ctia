(ns ctia.stores.es.schemas
  (:require [clj-momo.lib.es.schemas] ;; no alias to avoid ESConnState clashes
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnState
  (st/merge 
    ;; disallows services
    clj-momo.lib.es.schemas/ESConnState
    {:services {:ConfigService {:get-in-config (s/pred ifn?)}}}))
