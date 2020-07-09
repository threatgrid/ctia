(ns ctia.lib.riemann
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [riemann.client :as riemann]
            [ctia.properties :as prop]
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
  "provide how much ms were elapsed since `nano-start`."
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

;; should align with riemann-reporter.core/extract-infos-from-event
(defn extract-infos-from-event
  [event]
  (let [search-event get-in]
    (into {}
          (remove (comp nil? second))
          {:client-id (search-event [:client :id] event)
           :client-name (search-event [:client :name] event)
           :user-id (search-event [:user :id] event)
           :user-name (search-event [:user :name] event)
           :user-nick (search-event [:user :nick] event)
           :user-email (search-event [:user :email] event)
           :org-id (search-event [:org :id] event)
           :org-name (search-event [:org :name] event)
           :idp-id (search-event [:idp :id] event)
           :trace-id (search-event [:trace :id] event)})))

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
  [conn prefix event]
  (let [prepared-event (-> event
                           prepare-event
                           (update :service #(str prefix " " %)))]
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
  [handler service-name]
  (let [config (get-in @prop/properties [:ctia :log :riemann])
        _ (log/info "Riemann request logging initialization")
        send-event-fn 
        (let [client (-> (select-keys config
                                      [:host :port :interval-in-ms])
                         riemann/tcp-client
                         #_
                         (riemann/batch-client
                           (or (:batch-size config) 10)))]
          (fn [event]
            (send-event client service-name event)))]
    (fn [request]
      (let [start (System/nanoTime)]
        (try
          (when-let [response (handler request)]
            (let [ms (ms-elapsed start)]
              (send-request-logs send-event-fn request
                                 {:metric ms
                                  :service service-name
                                  :_headers (prn-str (:headers response))
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
              (send-request-logs send-event-fn request evt))
            (throw e))
          (catch Throwable e
            (send-request-logs send-event-fn request
                               {:metric (ms-elapsed start)
                                :service (str service-name " error")
                                :description (.getMessage e)
                                :stacktrace (.getStackTrace e)
                                :status "500"
                                :error "true"})
            (throw e)))))))
