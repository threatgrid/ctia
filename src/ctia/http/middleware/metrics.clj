(ns ctia.http.middleware.metrics
  "Middleware to control all metrics of the server"
  (:require [clout.core :as clout]
            [metrics
             [core :refer [default-registry remove-metric]]
             [meters :refer [mark! meter]]
             [timers :refer [time! timer]]]
            [metrics.jvm.core :as jvm]
            [metrics.ring.instrument :refer [instrument]]
            [slugger.core :refer [->slug]]))

(defn add-default-metrics
  []
  (jvm/register-memory-usage-gauge-set default-registry)
  (jvm/register-garbage-collector-metric-set default-registry)
  (jvm/register-thread-state-gauge-set default-registry))

(defn match-route? [[compiled-path _ verb] request]
  (if (= (name (:request-method request)) verb)
    (some? (clout/route-matches compiled-path request))
    false))

(defn matched-route [routes request]
  (first (filter #(match-route? % request) routes)))

(defn gen-metrics-for [handler routes]
  (let [reg default-registry
        ;; Time by swagger route
        times (reduce (fn [acc [_ path verb]]
                        (assoc-in acc [path verb]
                                  (timer reg ["ctia-time" path verb])))
                      {:unregistered (timer reg ["ctia-time" "_" "unregistered"])}
                      routes)
        ;; Meter by swagger route
        meters (reduce (fn [acc [_ path verb]]
                         (assoc-in acc [path verb]
                                   (meter reg ["ctia-req" path verb])))
                       {:unregistered (meter reg ["ctia-req" "_" "unregistered"])}
                       routes)]
    (fn [request]
      (let [route (or (matched-route routes request)
                      [:place_holder :unregistered])]
        (mark! (get-in meters (drop 1 route)))
        (time! (get-in times (drop 1 route)) (handler request))))))

(defn wrap-metrics [handler]
  (let [routes (compojure.api.routes/get-routes handler)
        exposed-routes (map (fn [l] [(clout/route-compile (first l))
                                    (->slug (first l))
                                    (name (second l))])
                            routes)]
    (add-default-metrics)
    (-> handler
        (instrument)
        (gen-metrics-for exposed-routes))))
