(ns ctia.test-helpers.redis
  (:require [ctia.test-helpers.core :as h]))

(defn fixture-properties:redis-store [f]
  ;; May be overridden with ENV variables
  (h/with-properties ["ctia.store.redis.enabled" true]
    (f)))
