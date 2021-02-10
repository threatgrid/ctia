(ns ctia.features-service
  (:require
   [clojure.string :as string]
   [ctia.entity.entities :as entities]
   [puppetlabs.trapperkeeper.core :as tk]))

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
  [[:ConfigService get-in-config]]
  (feature-flags [this]
    (get-in-config [:ctia :features]))
  (enabled?
   [this key]
   (when (-> (entities/all-entities)
             keys
             set
             (contains? key))
     (as-> (get-in-config [:ctia :features :disable]) x
       (str x)
       (string/split x #",")
       (map keyword x)
       (set x)
       (contains? x key)
       (not x)))))
