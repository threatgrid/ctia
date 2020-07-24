(ns ctia.flows.hooks-service
  (:require [ctia.flows.hooks-service-core :as core]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defonce global-hooks-service (atom nil))

(defprotocol HooksService
  (add-hook! [this hook-type hook]
             "Add a `Hook` for the hook `hook-type`")
  (add-hooks! [this hook-type hook-list]
              "Add a list of `Hook` for the hook `hook-type`")
  (apply-hooks [this hook-options]
               "Apply the registered hooks for a given hook-type to the passed in data.
               Data may be an entity (or an event) and a previous entity.  Accepts
               read-only?, in which case the result of the hooks do not change the result.
               In any hook returns nil, the result is ignored and the input entity is kept.")
  (apply-event-hooks [this event])
  (init-hooks! [this])
  (shutdown! [this])
  (reset-hooks! [this]))

(tk/defservice hooks-service
  HooksService
  []
  (init [this context]
        (reset! global-hooks-service this)
        (core/init context))
  (start [this context]
         (reset! global-hooks-service this)
         (core/start context))
  (stop [this context]
        (reset! global-hooks-service nil)
        (core/stop context))

  (add-hook! [this hook-type hook] (core/add-hook!
                                     (service-context this)
                                     hook-type
                                     hook))
  (add-hooks! [this hook-type hook-list] (core/add-hooks!
                                           (service-context this)
                                           hook-type
                                           hook-list))
  (apply-hooks [this hook-options] (core/apply-hooks
                                     (service-context this)
                                     hook-options))
  (apply-event-hooks [this event] (core/apply-event-hooks
                                    (service-context this)
                                    event))
  (init-hooks! [this] (core/init-hooks!
                        (service-context this)))
  (shutdown! [this] (core/shutdown!
                      (service-context this)))
  (reset-hooks! [this] (core/reset-hooks!
                         (service-context this))))
