(ns ctia.http.routes.stix12
  (:require [compojure.api.sweet
             :refer [defapi context GET]]
            [ctia.http.handlers.stix12.indicator :as indicator]
            [ring.util.http-response
             :refer [ok not-found]]
            [schema.core :as s]))

(defapi stix12-routes
  (context
   "/stix12" []

   (context
    "/indicator" []

    (GET "/:id" []
         :summary "Get an Indicator in STIX 1.2 XML format"
         :path-params [id :- s/Str]
         :header-params [api_key :- (s/maybe s/Str)]
         :capabilities #{:read-indicator :admin}
         (if-let [d (indicator/read-xml id)]
           (ok d)
           (not-found))))))
