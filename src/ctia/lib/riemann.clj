(ns ctia.lib.riemann
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [riemann.client :as riemann]
            [ctia.properties :as prop]
            [ctia.lib.utils :as utils])
  (:import [clojure.lang ExceptionInfo]))

(defn request->event
  [request extra-fields]
  (into {:uri (str (:uri request))
         :_params (utils/safe-pprint (:params request))
         :remote-addr (str (if-let [xff (get-in request [:headers "x-forwarded-for"])]
                             (peek (str/split xff #"\s*,\s*"))
                             (:remote-addr request)))
         :request-headers (prn-str (:headers request))
         :request-body (let [;; HttpInputOverHTTP => string
                             bstr (pr-str (:body request))]
                         (subs bstr 0 (min (count bstr) 100)))
         :request-method (str (:request-method request))
         :mask (str (= "Mask" (get-in request [:headers "x-client-app"])))
         :identity (:identity request)
         :jwt (:jwt request)}
        extra-fields))

(defn ms-elapsed
  "provide how much ms were elapsed since `nano-start`."
  [nano-start]
  (/ (- (System/nanoTime) nano-start) 1000000.0))

(defn- send-request-metrics [send-event-fn request extra-fields]
  (let [event (request->event request extra-fields)]
    (try
      (send-event-fn event)
      (catch Exception e
        (log/warnf "A Problem occured while sending request metrics event:\n\n%s\n\nException:\n%s"
                   (utils/safe-pprint event)
                   (utils/safe-pprint e))
        (when (nil? send-event-fn)
          (log/warn "The send-event-fn looks empty. This is certainly due to a configuration problem or perhaps simply a code bug."))))))

(defn wrap-request-metrics [handler service-name send-event-fn]
  (fn [request]
    (let [start (System/nanoTime)]
      (try
        (when-let [response (handler request)]
          (let [ms (ms-elapsed start)]
            (send-request-metrics send-event-fn request
                                  {:metric ms
                                   :tags ["ctia" "http"]
                                   :description (str "Response took "
                                                     (.format (java.text.DecimalFormat. "#.##")
                                                              (/ ms 1000))
                                                     " seconds")
                                   :service service-name
                                   :status (str (:status response))}))
          response)
        (catch ExceptionInfo e
          (let [data (ex-data e)
                ex-type (:type data)
                evt {:metric (ms-elapsed start)
                     :service (str service-name " error")
                     :description (.getMessage e)
                     :error "true"
                     :status (condp = ex-type
                               :ring.util.http-response/response (get-in data [:response :status])
                               :compojure.api.exception/response-validation "500"
                               :compojure.api.exception/request-validation "400"
                               "500")}]
            (send-request-metrics send-event-fn request evt))
          (throw e))
        (catch Throwable e
          (send-request-metrics send-event-fn request
                                {:metric (ms-elapsed start)
                                 :service (str service-name " error")
                                 :description (.getMessage e)
                                 :stacktrace (.getStackTrace e)
                                 :status "500"
                                 :error "true"})
          (throw e))))))

(defn prepare-event [event]
  {:user-id (get-in event [:user :id])})

(defn send-event
  [conn prefix event]
  (let [prepared-event (-> event
                           prepare-event
                           (update :service #(str prefix " " %)))]
    (if conn
      (do
        (log/debugf "Sending event to riemann:\n%s\nprepared:\n%s"
                    (utils/safe-pprint event)
                    (utils/safe-pprint prepared-event))
        (riemann/send-event conn prepared-event))
      (when-not (= "log" (:event-type prepared-event))
        (log/warnf "Riemann doesn't seem configured. Event: %s"
                   (utils/safe-pprint prepared-event))))))

(defn wrap-request-logs
  "Middleware to log all incoming connections to Riemann"
  [service-name]
  (let [{enabled? :enabled :as config}
        (get-in @prop/properties [:ctia :log :riemann])]
    (if-not enabled?
      identity
      (let [_ (log/info "riemann metrics reporting")
            send-event-fn 
            (let [client (riemann/tcp-client
                           (select-keys config
                                        [:host :port :interval-in-ms]))]
              (fn [event]
                (riemann/send-event client event)))]
        (fn [handler]
          (wrap-request-metrics handler
                                service-name
                                send-event-fn))))))

(defn log
  "Produce a log and send an event to riemann.
  The event should contains the fields :level to specify the log level.
  The r-service parameter should be the service name that appear in riemann."
  [_ r-service msg event]
  '...)
