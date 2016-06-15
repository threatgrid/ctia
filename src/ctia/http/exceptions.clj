(ns ctia.http.exceptions
  "This ns declare all handler for server exceptions.

  See https://github.com/metosin/compojure-api/wiki/Exception-handling"
  (:require
   [compojure.api.impl.logging :as logging]
   [compojure.api.exception :as ex]
   [ring.util.http-status :refer [internal-server-error]]))

(defn request-parsing-handler
  "Handle request parsing error"
  [^Exception e data request]
  (logging/log! :error e (.getMessage e))
  (ex/request-parsing-handler e data request))

(defn request-validation-handler
  "Handle response coercion error"
  [^Exception e data request]
  (logging/log! :error e (.getMessage e))
  (ex/request-validation-handler e data request))

(defn response-validation-handler
  "Handle request coercion error"
  [^Exception e data request]
  (logging/log! :error e (.getMessage e))
  (ex/response-validation-handler e data request))


