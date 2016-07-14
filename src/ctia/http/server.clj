(ns ctia.http.server
  (:require [ctia.http.handler :as handler]
            [ctia.properties :refer [properties]]
            [ctia.shutdown :as shutdown]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]])
  (:import org.eclipse.jetty.server.Server))

(defonce server (atom nil))

(defn- new-jetty-instance [{:keys [dev-reload max-threads min-threads port]
                            :as _http-config_}]
  (doto
      (jetty/run-jetty (if dev-reload
                         (wrap-reload #'handler/api-handler)
                         #'handler/api-handler)
                       {:port port
                        :min-threads min-threads
                        :max-threads max-threads
                        :join? false})
    (.setStopAtShutdown true)
    (.setStopTimeout (* 1000 10))))

(defn- stop!  []
  (swap! server
         (fn [^Server server]
           (when server
             (.stop server))
           nil)))

(defn start! [& {:keys [join?]
                 :or {join? true}}]
  (let [server-instance (new-jetty-instance (get-in @properties [:ctia :http]))]
    (reset! server server-instance)
    (shutdown/register-hook! :http.server stop!)
    (if join?
      (.join server-instance)
      server-instance)))
