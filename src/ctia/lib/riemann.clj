(ns ctia.lib.riemann)

(defn wrap-request-logs
  "Middleware to log all incoming connections to Riemann"
  [_ prefix]
  (fn [handler]
    handler))

(defn log
  "Produce a log and send an event to riemann.
  The event should contains the fields :level to specify the log level.
  The r-service parameter should be the service name that appear in riemann
  it has nothing to do with tk services."
  [_ r-service msg event]
  '...)
