(ns ctia.flows.event-hook
  (:require
   [ctia.events.obj-to-event :refer [to-create-event
                                     to-update-event
                                     to-delete-event]]
   [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.events.producer :as p]))

(defrecord EventHookRecord []
  Hook
  (init [_] :nothing)
  (destroy [_] :nothing)
  (handle [_ _ object _]
    (p/produce object)))

(def EventHook (EventHookRecord.))
