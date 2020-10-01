(ns ctia.test-helpers.benchmark
  (:require [clj-momo.lib.net :as net]
            [ctia
             [init :refer [start-ctia!]]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]
            [puppetlabs.trapperkeeper.app :as app]))

(defn setup-ctia! [fixture]
  (let [http-port (net/available-port)
        app (fixture (fn []
                       (helpers/with-properties ["ctia.store.es.default.refresh" "false"
                                                 "ctia.http.enabled" true
                                                 "ctia.http.port" http-port
                                                 "ctia.http.bulk.max-size" 100000
                                                 "ctia.http.show.port" http-port]
                         (start-ctia!))))]
    {:port http-port
     :app app}))


(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(def delete-store-indexes esh/delete-store-indexes)

(defn cleanup-ctia! [app]
  (esh/delete-store-indexes false)
  (let [{{:keys [request-shutdown	
                 wait-for-shutdown]} :ShutdownService} (app/service-graph app)]
    (request-shutdown)
    (wait-for-shutdown)))
