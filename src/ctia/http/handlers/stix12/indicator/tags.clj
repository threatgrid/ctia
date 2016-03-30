(ns ctia.http.handlers.stix12.indicator.tags
  (:require [clojure.string :as str])
  (:import [org.mitre.stix.common_1
            StructuredTextType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn tags? [indicator]
  (boolean (:tags indicator)))

(defn attach-tags [^Indicator xml-indicator indicator]
  (.add (.getDescriptions xml-indicator)
        (doto (StructuredTextType.)
          (.setValue (str "tags: "
                          (str/join ", " (:tags indicator))))))
  xml-indicator)
