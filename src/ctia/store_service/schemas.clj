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

(s/defschema StoresAtom 
  (s/pred #(instance? clojure.lang.IAtom2 %)))

(s/defschema Context
  {:services Services
   :stores-atom StoresAtom})
