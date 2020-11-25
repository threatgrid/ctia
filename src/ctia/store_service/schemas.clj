(ns ctia.store-service.schemas
  (:require [ctia.ductile-service.schemas :as ductile]
            [ctia.schemas.utils :as csu]
            [schema.core :as s]))

(s/defschema Services
  {:ConfigService {:get-in-config (s/pred ifn?)} ;;TODO
   :DuctileService (-> ductile/ServiceGraph
                       (csu/select-all-keys [:request-fn]))})

(s/defschema Context
  {:services Services
   :stores-atom (s/pred #(instance? clojure.lang.IAtom2 %))})
