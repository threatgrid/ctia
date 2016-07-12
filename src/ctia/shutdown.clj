(ns ctia.shutdown
  (:require [clojure.tools.logging :as log]))

(defonce shutdown-hooks (atom {:registered? false :hooks {}}))

(defn register-hook! [key f]
  (swap! shutdown-hooks assoc-in [:hooks key] f))

(defn shutdown-ctia! []
  (doseq [[name hook] (:hooks @shutdown-hooks)]
    (try
      (hook)
      (catch Exception excep
        (log/error excep (format "Shutdown hook %s failed" name)))))
  (swap! shutdown-hooks assoc :hooks {}))

(defn init! []
  (when-not (:registered? @shutdown-hooks)
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. shutdown-ctia!))
    (swap! shutdown-hooks assoc :registered? true)))
