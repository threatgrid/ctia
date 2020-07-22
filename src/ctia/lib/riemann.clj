(ns ctia.lib.riemann
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [riemann.client :as riemann]
            [ctia.properties :as p]
            [ctia.lib.utils :as utils])
  (:import [clojure.lang ExceptionInfo]))

;; based on riemann-reporter.core/request-to-event
(defn request->event
  [request extra-fields]
  (into {:uri (str (:uri request))
         :_params (utils/safe-pprint-str (:params request))
         :remote-addr (str (if-let [xff (get-in request [:headers "x-forwarded-for"])]
                             (peek (str/split xff #"\s*,\s*"))
                             (:remote-addr request)))
         :request-method (str (:request-method request))
         :identity (:identity request)
         :jwt (:jwt request)}
        extra-fields))

(defn ms-elapsed
  "Milliseconds since `nano-start`."
  [nano-start]
  (/ (- (System/nanoTime) nano-start) 1000000.0))

;; based on riemann-reporter.core/send-request-metrics
(defn- send-request-logs [send-event-fn request extra-fields]
  (let [event (request->event request extra-fields)]
    (try
      (send-event-fn event)
      (catch Exception e
        (log/warnf "A Problem occured while sending request metrics event:\n\n%s\n\nException:\n%s"
                   (utils/safe-pprint-str event)
                   (utils/safe-pprint-str e))
        (when (nil? send-event-fn)
          (log/warn "The send-event-fn looks empty. This is certainly due to a configuration problem or perhaps simply a code bug."))))))

(def kw->jwt-key
  (into {}
        (map (fn [[k v]]
               [k (str "https://schemas.cisco.com/iroh/identity/claims/" v)]))

        {:client-id   "oauth/client/id"
         :client-name "oauth/client/name"
         :user-id     "user/id"
         :user-name   "user/name"
         :user-nick   "user/nick"
         :user-email  "user/email"
         :org-id      "org/id"
         :org-name    "org/name"
         :idp-id      "user/idp/id"
         ;; :trace-id (search-event [:trace :id] event) ??
         }))

;; should align with riemann-reporter.core/extract-infos-from-event
(defn extract-infos-from-event
  [event]
  (into {}
        (map (fn [[kw jwt-key]]
               (when-let [v (get-in event [:jwt jwt-key])]
                 [kw v])))
        kw->jwt-key))

(defn find-and-add-metas
  [e]
  (let [infos (extract-infos-from-event e)]
    (into infos e)))

;; copied from riemann-reporter.core
(defn str-protect [s]
  (str/replace s #"[^a-zA-Z_-]" "-"))

;; copied from riemann-reporter.core
(defn- deep-flatten-map-as-couples
  [prefix m]
  (apply concat
         (for [[k v] m]
           (let [k-str (if (keyword? k) (name k) (str k))
                 new-pref (if (empty? prefix)
                            k-str
                            (str (name prefix)
                                 "-"
                                 (str-protect k-str)))]
             (if (map? v)
               (deep-flatten-map-as-couples new-pref v)
               [[(keyword new-pref)
                 (if (string? v) v (pr-str v))]])))))

;; copied from riemann-reporter.core
(defn deep-flatten-map
  [prefix m]
  (into {}
        (deep-flatten-map-as-couples prefix m)))

;; copied from riemann-reporter.core
(defn stringify-values [m]
  (into
   (deep-flatten-map "" (dissoc m :tags :time :metric))
   (select-keys m [:tags :time :metric])))

;; copied from riemann-reporter.core
(defn prepare-event
  "remove nils, stringify, edn-encode unencoded underscored keys"
  [event]
  (->> event
       utils/deep-remove-nils
       (into {})
       utils/deep-filter-out-creds
       find-and-add-metas
       stringify-values))

;; based on riemann-reporter.core/send-event
(defn send-event
  [conn service-prefix event]
  (let [prepared-event (-> event
                           prepare-event
                           (update :service #(str service-prefix " " %)))]
    (if conn
      (do
        (log/debugf "Sending event to riemann:\n%s\nprepared:\n%s"
                    (utils/safe-pprint-str event)
                    (utils/safe-pprint-str prepared-event))
        (riemann/send-event conn prepared-event))
      (when-not (= "log" (:event-type prepared-event))
        (log/warnf "Riemann doesn't seem configured. Event: %s"
                   (utils/safe-pprint-str prepared-event))))))

;; based on riemann-reporter.core/wrap-request-metrics
(defn wrap-request-logs
  "Middleware to log all incoming connections to Riemann"
  [handler metric-description]
  (let [_ (assert (and (string? metric-description)
                       (seq metric-description))
                  (pr-str metric-description))
        _ (log/info "Riemann request logging initialization")
        send-event-fn 
        (let [config (get-in @(p/get-global-properties) [:ctia :log :riemann])
              client (-> (select-keys config
                                      [:host :port :interval-in-ms])
                         riemann/tcp-client
                         (riemann/batch-client
                           (or (:batch-size config) 10)))
              service-prefix (or (:service-prefix config) "CTIA")]
          (fn [event]
            (send-event client service-prefix event)))]
    (fn [request]
      (let [start (System/nanoTime)]
        (try
          (when-let [response (handler request)]
            (let [ms (ms-elapsed start)]
              (send-request-logs send-event-fn request
                                 {:metric ms
                                  :service metric-description
                                  :_headers (prn-str (:headers response))
                                  :status (str (:status response))}))
            response)
          (catch ExceptionInfo e
            (let [data (ex-data e)
                  ex-type (:type data)
                  evt {:metric (ms-elapsed start)
                       :service (str metric-description " error")
                       :description (.getMessage e)
                       :error "true"
                       :status (condp = ex-type
                                 :ring.util.http-response/response (get-in data [:response :status])
                                 :compojure.api.exception/response-validation "500"
                                 :compojure.api.exception/request-validation "400"
                                 "500")}]
              (send-request-logs send-event-fn request evt))
            (throw e))
          (catch Throwable e
            (send-request-logs send-event-fn request
                               {:metric (ms-elapsed start)
                                :service (str metric-description " error")
                                :description (.getMessage e)
                                :stacktrace (.getStackTrace e)
                                :status "500"
                                :error "true"})
            (throw e)))))))
