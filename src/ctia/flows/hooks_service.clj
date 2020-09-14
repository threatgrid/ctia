(ns ctia.flows.hooks-service)

#_
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

(defn lift-hooks-service-fns
  "Given a map of HooksService services (via defservice), lift
  them to support variable arguments.
  
  apply-hooks   - hook-options becomes keyword arguments"
  [services]
  (cond-> services
    (:apply-hooks services) (update :apply-hooks (fn [apply-hooks]
                                                   (fn [& {:as args}]
                                                     (apply-hooks args))))))
