(ns ctia.http.server
  (:require [ctia.http.handler :as handler]
            [ctia.properties :refer [properties]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [{:keys [dev-reload min-threads max-threads port]} (get-in @properties [:ctia :http])
        handler (if dev-reload
                  (wrap-reload handler/app)
                  handler/app)]
    (reset! server (jetty/run-jetty handler
                                    {:port port
                                     :min-threads min-threads
                                     :max-threads max-threads
                                     :join? join?}))))

(defn stop! []
  (swap! server
         (fn [^Server s]
           (when s (.stop s))
           nil)))
