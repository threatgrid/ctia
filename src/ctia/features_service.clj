(ns ctia.features-service
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.events-service-core :as core]
            [clojure.string :as string]))

(defprotocol FeaturesService
  "Service to read configuration properties below [:ctia :features]"
  (feature-flags [this])
  (disabled? [this key])
  (enabled? [this key]))

(tk/defservice features-service
  FeaturesService
  [[:ConfigService get-config get-in-config]]
  (feature-flags [this]
    (-> (get-config) :ctia :features))
  (disabled? [this key]
    (as-> [:ctia :features :disable] x
      (get-in-config x)
      (string/split x #",")
      (map keyword x)
      (set x)
      (contains? x key)))
  (enabled? [this key]
    (not (disabled? this key))))
