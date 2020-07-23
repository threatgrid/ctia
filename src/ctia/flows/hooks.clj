(ns ctia.flows.hooks
  "Handle hooks ([Cf. #159](https://github.com/threatgrid/ctia/issues/159))."
  (:require [ctia.flows.hooks-service :as hooks-svc]))

(defn apply-hooks
  "Apply the registered hooks for a given hook-type to the passed in data.
   Data may be an entity (or an event) and a previous entity.  Accepts
  read-only?, in which case the result of the hooks do not change the result.
  In any hook returns nil, the result is ignored and the input entity is kept."
  [& {:keys [hook-type
             entity
             prev-entity
             read-only?]
      :as hook-options}]
  (hooks-svc/apply-hooks @hooks-svc/global-hooks-service
                         hook-options))

(defn apply-event-hooks [event]
  (hooks-svc/apply-event-hooks @hooks-svc/global-hooks-service
                               event))
