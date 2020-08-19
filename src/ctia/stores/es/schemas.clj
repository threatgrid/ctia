(ns ctia.stores.es.schemas
  (:require [clj-momo.lib.es.schemas
             :refer [;; this namespace defines a ESConnState var
                     ;; meant as a direct replacement of the referred
                     ;; var below. We :rename it to ESConnStateNoServices
                     ;; to avoid clashes.
                     ESConnState]
             :rename {ESConnState ESConnStateNoServices}]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnState
  (st/merge 
    ESConnStateNoServices
    {:services {:ConfigService {:get-in-config (s/pred ifn?)}}}))
