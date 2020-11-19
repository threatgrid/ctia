(ns ctia.metrics.routes
  (:require
   [compojure.api.core :refer [context GET]]
   [ctia.http.routes.common :as routes.common]
   [metrics
    [core :refer [default-registry]]
    [counters :as counters]
    [gauges :as gauges]
    [histograms :as histograms]
    [meters :as meters]
    [timers :as timers]
    [utils :refer [all-metrics]]]
   [ring.util.http-response :refer [ok]]
   [schema.core :as s])
  (:import [com.codahale.metrics Counter Gauge Histogram Meter Timer]))

(defprotocol RenderableMetric
  (render-to-basic [metric]
    "Turn a metric into a basic Clojure datastructure."))

(extend-type Gauge
  RenderableMetric
  (render-to-basic [g]
    {:type :gauge
     :value (gauges/value g)}))

(extend-type Timer
  RenderableMetric
  (render-to-basic [t]
    {:type :timer
     :rates (timers/rates t)
     :percentiles (timers/percentiles t)
     :max (timers/largest t)
     :min (timers/smallest t)
     :mean (timers/mean t)
     :standard-deviation (timers/std-dev t)}))

(extend-type Meter
  RenderableMetric
  (render-to-basic [m]
    {:type :meter
     :rates (meters/rates m)}))

(extend-type Histogram
  RenderableMetric
  (render-to-basic [h]
    {:type :histogram
     :max (histograms/largest h)
     :min (histograms/smallest h)
     :mean (histograms/mean h)
     :standard-deviation (histograms/std-dev h)
     :percentiles (histograms/percentiles h)}))

(extend-type Counter
  RenderableMetric
  (render-to-basic [c]
    {:type :counter
     :value (counters/value c)}))

(defn- render-metric [[metric-name metric]]
  [metric-name (render-to-basic metric)])

(defn render-metrics []
  (into {} (map render-metric (all-metrics default-registry))))

(defn metrics-routes []
  (let [capabilities :developer]
    (context "/metrics" []
      :tags ["Metrics"]
      (GET "/" []
        :summary "Display Metrics"
        :description (routes.common/capabilities->description capabilities)
        :capabilities capabilities
        (ok (render-metrics))))))
