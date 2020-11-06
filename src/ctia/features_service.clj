(ns ctia.features-service
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [clojure.string :as string]))

(defprotocol FeaturesService
  "Service to read configuration properties under [:ctia :features] key in config.

  If an option like this: `ctia.features.disable=asset,actor,sighting` is added
  to the properties file (ctia-default.properties) - http routes for Asset,
  Actor, and Sighting entities would be disabled and their respective contexts
  would not be visible in Swagger console."
  (feature-flags [this]
    "Returns all feature flags defined in the config")
  (enabled? [this key]
    "Returns `true` unless entity key is marked as Disabled in properties config"))

(tk/defservice features-service
  FeaturesService
  [[:ConfigService get-config get-in-config]]
  (feature-flags [this]
    (-> (get-config) :ctia :features))
  (enabled?
   [this key]
   (as-> [:ctia :features :disable] x
     (get-in-config x)
     (str x)
     (string/split x #",")
     (map keyword x)
     (set x)
     (contains? x key)
     (not x))))
