(ns ctia.test-helpers.benchmark
  (:require [clj-momo.lib.net :as net]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]
            [puppetlabs.trapperkeeper.app :as app]))

(defn setup-ctia! [fixture]
  (let [app (helpers/with-properties ["ctia.store.es.default.refresh" "false"
                                      "ctia.http.bulk.max-size" 100000]
              (helpers/fixture-ctia
                fixture))
        get-in-config (helpers/current-get-in-config-fn app)]
    {:port (get-in-config [:ctia :http :port])
     :app app}))

(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(def delete-store-indexes esh/delete-store-indexes)

(defn cleanup-ctia! [app]
  (esh/delete-store-indexes app false)
  (let [{{:keys [request-shutdown
                 wait-for-shutdown]} :ShutdownService} (app/service-graph app)]
    (request-shutdown)
    (wait-for-shutdown)))
