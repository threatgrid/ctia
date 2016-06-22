(ns ctia.lib.metrics.riemann
  (:require [metrics.core :refer [default-registry] :as m]
            [metrics.gauges :as gauges]
            [metrics.meters :as meters]
            [metrics.histograms :as histograms]
            [metrics.counters :as counters]
            [metrics.timers :as timers]
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

(defn make-event [protected-name suffix value]
  (let [name (->riemann-name protected-name)]
    ;; Riemann can't display non number gauge values
    (when (number? value)
      {:service (str name " " suffix)
       :state "ok"
       :metric value})))

(defn counter-metrics [counters]
  (map (fn [[name counter]]
         (make-event name "count" (counters/value counter)))
       counters))

(defn gauge-metrics [gauges]
  (map (fn [[name gauge]]
         (make-event name "value" (gauges/value gauge)))
       gauges))

(defn histogram-metrics [histograms]
  (mapcat (fn [[name histogram]]
            (concat
             [(make-event name "mean" (histograms/mean histogram))
              (make-event name "max" (histograms/largest histogram))
              (make-event name "min" (histograms/smallest histogram))
              (make-event name "stddev" (histograms/std-dev histogram))]
             (let [percentiles (histograms/percentiles histogram)]
               (map
                (fn [[k v]]
                  (make-event name (str k "_percentiles") v))
                percentiles))))
          histograms))

(defn meter-metrics [meters]
  (mapcat (fn [[name meter]]
            [(make-event name "count" (meters/count meter))
             (make-event name "1min" (meters/rate-one meter))
             (make-event name "5min" (meters/rate-five meter))
             (make-event name "15min" (meters/rate-fifteen meter))
             (make-event name "mean" (meters/rate-mean meter))])
          meters))

(defn timer-metrics [timers]
  (mapcat (fn [[name timer]]
            (concat
             [(make-event name "1min" (timers/rate-one timer))
              (make-event name "5min" (timers/rate-five timer))
              (make-event name "15min" (timers/rate-fifteen timer))
              (make-event name "mean" (timers/rate-mean timer))
              (make-event name "max" (timers/largest timer))
              (make-event name "min" (timers/smallest timer))
              (make-event name "stddev" (timers/std-dev timer))
              ]
             (let [percentiles (timers/percentiles timer)]
               (map
                (fn [[k v]]
                  (make-event name (str k "_percentiles") v))
                percentiles)))
            )
          timers))

(defn metrics-from-reg [reg]
  {:counters   (counter-metrics (m/counters reg))
   :gauges     (gauge-metrics (m/gauges reg))
   :histograms (histogram-metrics (m/histograms reg))
   :meters     (meter-metrics (m/meters reg))
   :timers     (timer-metrics (m/timers reg))})

(defn send-events []
  (let [metrics (metrics-from-reg default-registry)]
    (doseq [event (remove nil? (apply concat (vals metrics)))]
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
    (when enabled
      (log/info "riemann metrics reporting")
      (reset! client (r/tcp-client {:host host :port port}))
      (.start (Thread. periodically-send-events)))))
