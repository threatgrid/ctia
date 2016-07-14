(ns ctia.shutdown
  (:require [clojure.tools.logging :as log]))

(defonce shutdown-hooks (ref {:registered? false :hooks {}}))

(defn register-hook! [key f]
  (dosync
   (alter shutdown-hooks assoc-in [:hooks key] f)))

(defn shutdown-ctia!
  "Sequentially executes shutdown hooks.
   Empties the shutdown-hooks in a transaction, capturing the cleared
   hooks, and then executes each hook in a try...catch.  Since this may
   be called at shutdown time, we avoid using the agent-send-off pool."
  []
  (doseq [[name hook] (dosync
                       (let [removed-hooks (:hooks @shutdown-hooks)]
                         (alter shutdown-hooks assoc :hooks {})
                         removed-hooks))]
    (try
      (hook)
      (catch Exception excep
        (log/error excep (format "Shutdown hook %s failed" name))))))

(defn init! []
  (let [side-effect-agent (agent nil)]
    (dosync
     (when-not (:registered? @shutdown-hooks)
       (alter shutdown-hooks assoc :registered? true)
       (send side-effect-agent
             (fn [_]
               (.addShutdownHook
                (Runtime/getRuntime)
                (Thread. shutdown-ctia!))))))))
