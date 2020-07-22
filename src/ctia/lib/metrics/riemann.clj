(ns ctia.lib.metrics.riemann
  (:require [clj-momo.lib.metrics.riemann :as riemann]
            [ctia.properties :refer [properties]]
            [clojure.tools.logging :as log]))

(defn init! []
  (let [{enabled? :enabled :as config}
        (get-in @(get-global-properties) [:ctia :metrics :riemann])]
    (when enabled?
      (log/info "riemann metrics reporting")
      (riemann/start (select-keys config
                                  [:host :port :interval-in-ms])))))
