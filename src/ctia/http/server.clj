(ns ctia.http.server
  (:require [ctia.http.handler :as handler]
            [ctia.properties :refer [properties]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn- new-jetty-instance [{:keys [dev-reload max-threads min-threads port]
                            :as _http-config_}]
  (doto
      (jetty/run-jetty (if dev-reload
                         (wrap-reload #'handler/app)
                         #'handler/app)
                       {:port port
                        :min-threads min-threads
                        :max-threads max-threads
                        :join? false})
      (.setStopAtShutdown true)
      (.setStopTimeout (* 1000 10))))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [server-instance (new-jetty-instance (get-in @properties [:ctia :http]))]
    (reset! server server-instance)
    (if join?
      (.join server-instance)
      server-instance)))

(defn stop! []
  (swap! server
         (fn [^Server server]
           (when server
               (.stop server))
           nil)))
