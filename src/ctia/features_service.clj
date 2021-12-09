(ns ctia.features-service
  (:require
   [clojure.string :as string]
   [ctia.entity.entities :as entities]
   [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol FeaturesService
  "Service to read configuration properties under [:ctia :features] and [:ctia :feature-flags] keys in config.

  If an option like this: `ctia.features.disable=asset,actor,sighting` is added to
  the properties file (ctia-default.properties) - http routes for Asset, Actor, and
  Sighting entities would be disabled and their respective contexts would not be
  visible in Swagger console.

  Also, can be used to control arbitrary feature flags used for development,
  key/value pairs, e.g., `ctia.feature-flags=easter_egg:on, experimental_ratio:35`"
  (feature-flags [this]
    "Returns all feature flags defined in the config with their values")
  (flag-value [this key]
    "Returns value of a feature flag")
  (entities [this]
    "Returns map of all entities")
  (entity-enabled? [this key]
    "Returns `true` unless entity key is marked as Disabled in properties config"))

(tk/defservice features-service
  FeaturesService
  [[:ConfigService get-in-config]]
  (feature-flags
   [this]
   (when-let [fgs-str (some-> [:ctia :feature-flags]
                              get-in-config
                              (string/split #","))]
     (->> fgs-str
          (map
           (comp
            (fn [[k v]] (hash-map
                         (keyword (string/trim k))
                         (string/trim v)))
            (fn [s] (string/split s #":"))
            string/trim))
          (apply merge))))

  (flag-value
   [this key]
   (get (feature-flags this) key))

  (entities
   [this]
   (entities/all-entities))

  (entity-enabled?
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
