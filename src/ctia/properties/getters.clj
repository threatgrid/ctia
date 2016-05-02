(ns ctia.properties.getters
  (:import [java.net URI]))

(def default-redis-port
  "The default port to use for Redis when not configured"
  6379)

(def default-redis-host
  "The default address to connect to Redis at"
  "127.0.0.1")

(defn parse-host-port [{:keys [host port uri] :as _redis-config_}]
  (if-let [redis-url (and uri (URI. uri))]
    [(.getHost redis-url) (.getPort redis-url)]
    [(or host default-redis-host)
     (or port default-redis-port)]))

(defn redis-host-port
  [props]
  (parse-host-port (get-in props [:ctia :hook :redis])))
