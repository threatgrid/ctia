(ns ctia.http.exceptions
  "This ns declare all handler for server exceptions.

  See <https://github.com/metosin/compojure-api/wiki/Exception-handling>"
  (:import clojure.lang.ExceptionInfo)
  (:require [compojure.api.exception :as ex]
            [compojure.api.impl.logging :as logging]
            [ring.util.http-response :refer [internal-server-error
                                             bad-request
                                             forbidden]]
            [clojure.data.json :as json]))

(defn ex-message-and-data [^Exception e]
  (str
   (.getMessage e)
   (when (instance? ExceptionInfo e)
     (str " - Meta: " (pr-str (ex-data e))))))

(defn request-parsing-handler
  "Handle request parsing error"
  [^Exception e data request]
  (logging/log! :error e (ex-message-and-data e))
  (ex/request-parsing-handler e data request))

(defn request-validation-handler
  "Handle response coercion error"
  [^Exception e data request]
  (logging/log! :error e (ex-message-and-data e))
  (ex/request-validation-handler e data request))

(defn response-validation-handler
  "Handle request coercion error"
  [^Exception e data request]
  (logging/log! :error e (ex-message-and-data e))
  (ex/response-validation-handler e data request))

(defn es-ex-data
  [e data request]
  (let [exception-data (ex-data e)]
    (cond-> {:request request
             :data data}
      exception-data
      (assoc :ex-data exception-data))))

(defn es-invalid-request
  "Handle ES query parsing error"
  [^Exception e data request]
  (logging/log! :error e (es-ex-data e data request))
  (let [es-message (some-> e
                           ex-data
                           :es-http-res
                           :body
                           (json/read-str :key-fn keyword))]

    (bad-request
     {:type "ES Invalid Request"
      :message (some-> es-message
                       :error
                       :root_cause
                       first
                       :reason)
      :class (.getName (class e))})))

(defn access-control-error-handler
  "Handle access control error"
  [^Exception e _data _request]
  (logging/log! :info e (ex-message-and-data e))
  (forbidden
   {:error "Access Control validation Client Error"
    :type "Access Control Error"
    :message (.getMessage e)
    :class (.getName (class e))}))

(defn spec-validation-error-handler
  "Handle spec validation error"
  [^Exception e _data _request]
  (logging/log! :info e (ex-message-and-data e))
  (bad-request
   (let [entity (:entity (ex-data e))]
     {:error "Spec Validation Client Error"
      :type "Invalid Entity Error"
      :message (.getMessage e)
      :entity entity
      :class (.getName (class e))})))

(defn invalid-tlp-error-handler
  "Handle access control error"
  [^Exception e _data _request]
  (logging/log! :info e (ex-message-and-data e))
  (bad-request
   (let [entity (:entity (ex-data e))]
     {:type "Invalid TLP Error"
      :message (.getMessage e)
      :entity entity
      :class (.getName (class e))})))

(defn realize-entity-error-handler
  "Handle error at the realize step"
  [^Exception e _data _request]
  (logging/log! :info e (ex-message-and-data e))
  (bad-request
   (let [data (ex-data e)]
     (dissoc data :type))))

(defn default-error-handler
  "Handle default error"
  [^Exception e _data _request]
  (logging/log! :error e (ex-message-and-data e))
  (internal-server-error {:type "unknown-exception"
                          :class (.getName (class e))}))
