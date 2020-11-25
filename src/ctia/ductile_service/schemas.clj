(ns ctia.ductile-service.schemas
  (:require [schema.core :as s]))

(s/defschema RequestFn
  "Corresponds to an implementation of ctia.ductile-service/request-fn.
  
  Has the same contract as clj-http.client/request."
  (s/=> (s/named s/Any 'response)
        (s/named s/Any 'request)))

(s/defschema ServiceGraph
  "All methods provided by ctia.ductile-service/DuctileService,
  as a service graph entry."
  {:request-fn RequestFn})
