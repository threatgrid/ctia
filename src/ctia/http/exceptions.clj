(ns ctia.http.exceptions
  "This ns declare all handler for server exceptions.

  See <https://github.com/metosin/compojure-api/wiki/Exception-handling>"
  (:import clojure.lang.ExceptionInfo)
  (:require [compojure.api.exception :as ex]
            [compojure.api.impl.logging :as logging]
            [ring.util.http-response :refer [internal-server-error
                                             bad-request
                                             unauthorized]]
            [clojure.data.json :as json]))

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

(defn es-query-parsing-error-handler
  "Handle ES query parsing error"
  [^Exception e data request]
  (logging/log! :warn e (ex-message e))
  (let [es-message (some-> e
                           ex-data
                           :es-http-res
                           :body
                           (json/read-str :key-fn keyword))]

    (bad-request
     {:type "ES query parsing error"
      :message (some-> es-message
                       :error
                       :root_cause
                       first
                       :reason)
      :class (.getName (class e))})))

(defn access-control-error-handler
  "Handle access control error"
  [^Exception e data request]
  (logging/log! :info e (ex-message e))
  (unauthorized
   {:type "Access Control Error"
    :message (.getMessage e)
    :class (.getName (class e))}))

(defn spec-validation-error-handler
  "Handle spec validation error"
  [^Exception e data request]
  (logging/log! :info e (ex-message e))
  (bad-request
   (let [entity (:entity (ex-data e))]
     {:type "Invalid Entity Error"
      :message (.getMessage e)
      :entity entity
      :class (.getName (class e))})))

(defn invalid-tlp-error-handler
  "Handle access control error"
  [^Exception e data request]
  (logging/log! :info e (ex-message e))
  (bad-request
   (let [entity (:entity (ex-data e))]
     {:type "Invalid TLP Error"
      :message (.getMessage e)
      :entity entity
      :class (.getName (class e))})))

(defn default-error-handler
  "Handle default error"
  [^Exception e data request]
  (logging/log! :error e (ex-message e))
  (internal-server-error {:type "unknown-exception"
                          :class (.getName (class e))}))
