(ns ctia.publish
  (:require [ctia.lib.async :as la]
            [ctia.subscribe :as sub]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a]
            [schema.core :as s]))

(s/defn init!
  "Initializes publishing. Right now, this means Redis."
  [e :- la/EventChannel]
  (when (redis/enabled?)
    (sub/init!)
    (la/register-listener e redis/publish-fn (constantly true) nil)))

(defn shutdown! []
  (sub/shutdown!))
