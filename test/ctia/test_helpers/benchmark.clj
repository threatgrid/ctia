(ns ctia.test-helpers.benchmark
  (:require [criterium.core :as bench]
            [ctia.init :refer [start-ctia!]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as esh]]
            [puppetlabs.trapperkeeper.app :as app]))

(defn setup-ctia! [fixture]
  (let [app (fixture
              (fn []
                (helpers/with-properties ["ctia.store.es.default.refresh" "false"
                                          "ctia.http.bulk.max-size" 100000]
                  (helpers/fixture-ctia-with-app
                    identity))))]
    {:port (helpers/get-http-port app)
     :app app}))

(defn fixture-ctia-with-app [fixture-with-app]
  (when (System/getenv "GITHUB_ACTIONS")
    ;; fix estimated overhead (as recommended by criterium's readme) to a value source
    ;; during a run on GitHub Actions.
    (alter-var-root #'bench/estimated-overhead-cache (constantly 1.286691260245356E-8)))
  (helpers/with-properties ["ctia.store.es.default.refresh" "false"
                            "ctia.http.bulk.max-size" 100000]
    (helpers/fixture-ctia-with-app
      fixture-with-app)))

(defn setup-ctia-es-store! []
  (setup-ctia! esh/fixture-properties:es-store))

(defn fixture-ctia-es-store-with-app [fixture-with-app]
  (esh/fixture-properties:es-store
    #(fixture-ctia-with-app
       (fn [app]
         (try
           (fixture-with-app app)
           (finally
             (esh/delete-store-indexes app false)))))))

(def delete-store-indexes esh/delete-store-indexes)

(defn cleanup-ctia! [app]
  (esh/delete-store-indexes app false)
  (let [{{:keys [request-shutdown
                 wait-for-shutdown]} :ShutdownService} (app/service-graph app)]
    (request-shutdown)
    (wait-for-shutdown)))
