(ns ctia.flows.hooks-service.schemas
  (:require [ctia.flows.hook-protocol :refer [Hook]]
            [schema.core :as s]))

(s/defschema HookType
  (s/enum :before-create
          :after-create
          :before-update
          :after-update
          :before-delete
          :after-delete
          :event))

(s/defschema HooksMap
  "Map from hook type to registered hooks of that type."
  {HookType (s/both (schema/pred vector?)
                    [(s/protocol Hook)])})

(s/defschema Context
  "Context for default implementation of HooksService"
  {:hooks (s/atom HooksMap)})

(s/defschema EntityOrEvent
  "Depending on the corresponding :hook-type,
  should be an entity or event."
  s/Any)

(s/defschema ApplyHooksOptions
  "Options argument of apply-hooks."
  {:hook-type HookType
   :entity EntityOrEvent
   (s/optional-key :prev-entity) s/Any
   (s/optional-key :read-only?) s/Bool})

(s/defschema ServiceFns
  "Service functions corresponding to ctia.flows.hooks-service/HooksService"
  {:add-hook! (s/=> HooksMap
                    HookType
                    (s/protocol Hook))
   :add-hooks! (s/=> HooksMap
                     HookType
                     [(s/protocol Hook)])
   :apply-hooks (s/=> s/Any
                      ApplyHooksOptions)
   :apply-event-hooks (s/=> s/Any
                            EntityOrEvent)
   :init-hooks! (s/=> s/Any)
   :shutdown! (s/=> s/Any)
   :reset-hooks! (s/=> s/Any)})
