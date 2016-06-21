(ns ctia.http.middleware.metrics.riemann
  (:require [metrics.core :refer [default-registry]]
            [riemann.client :as r]
            [ctia.properties :refer [properties]]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [java.util.concurrent TimeUnit]
           [com.codahale.metrics MetricFilter]))

(def client (atom nil))

(defn ->riemann-name [protected-name]
  (-> protected-name
      (str/replace #"slash-" "/")
      (str/replace #"[-.]" " ")))

(defn counter-metrics [counters]
  (map (fn [[protected-name counter]]
         (let [name (->riemann-name protected-name)]
           {:service (str name " count") :state "ok" :metric (.getCount counter)}))
       counters))

(defn gauge-metrics [gauges]
  (map (fn [[protected-name gauge]]
         (let [name (->riemann-name protected-name)]
           {:service (str name " count") :state "ok" :metric (.geValue gauge)}))
       gauges))

(defn histogram-metrics [histograms]
  (mapcat (fn [[protected-name histogram]]
            (let [name (->riemann-name protected-name)
                  s (.getSnapshot histogram)]
              [{:service (str name " count") :state "ok" :metric (.geCount histogram)}
               {:service (str name " mean")  :state "ok" :metric (.getMean s)}
               {:service (str name " median")  :state "ok" :metric (.getMedian s)}
               {:service (str name " 75th_percentile")  :state "ok" :metric (.get75thPercentile s)}
               {:service (str name " 95th_percentile")  :state "ok" :metric (.get95thPercentile s)}
               {:service (str name " 98th_percentile")  :state "ok" :metric (.get98thPercentile s)}
               {:service (str name " 99th_percentile")  :state "ok" :metric (.get99thPercentile s)}
               {:service (str name " 999th_percentile")  :state "ok" :metric (.get999thPercentile s)}
               {:service (str name " max")  :state "ok" :metric (.getMax s)}
               {:service (str name " min")  :state "ok" :metric (.getMin s)}
               {:service (str name " stddev")  :state "ok" :metric (.getStdDev s)}]))
          histograms))

(defn meter-metrics [meters]
  (mapcat (fn [[protected-name meter]]
            (let [name (->riemann-name protected-name)]
              [{:service (str name " count") :state "ok" :metric (.getCount meter)}
               {:service (str name " 15min") :state "ok" :metric (.getFifteenMinuteRate meter)}
               {:service (str name " 5min")  :state "ok" :metric (.getFiveMinuteRate meter)}
               {:service (str name " 1min")  :state "ok" :metric (.getOneMinuteRate meter)}
               {:service (str name " mean")  :state "ok" :metric (.getMeanRate meter)}]))
          meters))

(defn timer-metrics [timers]
  (mapcat (fn [[protected-name timer]]
            (let [name (->riemann-name protected-name)
                  s (.getSnapshot timer)]
              (concat
               [{:service (str name " count") :state "ok" :metric (.getCount timer)}
                {:service (str name " 15min") :state "ok" :metric (.getFifteenMinuteRate timer)}
                {:service (str name " 5min")  :state "ok" :metric (.getFiveMinuteRate timer)}
                {:service (str name " 1min")  :state "ok" :metric (.getOneMinuteRate timer)}
                {:service (str name " mean")  :state "ok" :metric (.getMeanRate timer)}
                {:service (str name " median")  :state "ok" :metric (.getMedian s)}
                {:service (str name " 75th_percentile")  :state "ok" :metric (.get75thPercentile s)}
                {:service (str name " 95th_percentile")  :state "ok" :metric (.get95thPercentile s)}
                {:service (str name " 98th_percentile")  :state "ok" :metric (.get98thPercentile s)}
                {:service (str name " 99th_percentile")  :state "ok" :metric (.get99thPercentile s)}
                {:service (str name " 999th_percentile")  :state "ok" :metric (.get999thPercentile s)}
                {:service (str name " max")  :state "ok" :metric (.getMax s)}
                {:service (str name " min")  :state "ok" :metric (.getMin s)}
                {:service (str name " stddev")  :state "ok" :metric (.getStdDev s)}])))
          timers))

(defn metrics-from-reg [reg]
  {:counters   (counter-metrics (.getCounters reg))
   :gauges     (gauge-metrics (.getGauges reg))
   :histograms (histogram-metrics (.getHistograms reg))
   :meters     (meter-metrics (.getMeters reg))
   :timers     (timer-metrics (.getTimers reg))})

(defn send-events []
  (let [metrics (metrics-from-reg default-registry)]
    (doseq [event (apply concat (vals metrics))]
      (r/send-event @client event))))

(defn periodically-send-events []
  (while true
    (Thread/sleep (* (get-in @properties
                             [:ctia :metrics :riemann :interval])
                     1000))
    (send-events)))

(defn init! []
  (let [{:keys [enabled host port]}
        (get-in @properties [:ctia :metrics :riemann])]
    (reset! client (r/tcp-client {:host host :port port}))
    (.start (Thread. periodically-send-events))))
