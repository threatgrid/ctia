(ns ctia.http.exceptions
  "This ns declare all handler for server exceptions.

  See <https://github.com/metosin/compojure-api/wiki/Exception-handling>"
  (:require
   [compojure.api.impl.logging :as logging]
   [compojure.api.exception :as ex]
   [ring.util.http-response :refer [internal-server-error]])
  (import clojure.lang.ExceptionInfo))

(defn ex-message [^Exception e]
  (str
   (.getMessage e)
   (when (instance? ExceptionInfo e)
     (str " - Meta: " (pr-str (ex-data e))))))

(defn request-parsing-handler
  "Handle request parsing error"
  [^Exception e data request]
  (logging/log! :error e (ex-message e))
  (ex/request-parsing-handler e data request))

(defn request-validation-handler
  "Handle response coercion error"
  [^Exception e data request]
  (logging/log! :error e (ex-message e))
  (ex/request-validation-handler e data request))

(defn response-validation-handler
  "Handle request coercion error"
  [^Exception e data request]
  (logging/log! :error e (ex-message e))
  (ex/response-validation-handler e data request))

(defn default-error-handler
  "Handle default error"
  [^Exception e data request]
  (logging/log! :error e (ex-message e))
  (internal-server-error {:type "unknown-exception"
                          :class (.getName (class e))}))
