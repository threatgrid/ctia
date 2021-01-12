(ns ctia.flows.hooks-service-core
  "Handle hooks ([Cf. #159](https://github.com/threatgrid/ctia/issues/159))."
  (:require [clojure.tools.logging :as log]
            [ctia.flows.hooks.event-hooks :as event-hooks]
            [ctia.flows.hook-protocol :as prot]
            [ctia.schemas.services :refer [ConfigServiceFns]]
            [ctia.flows.hooks-service.schemas :refer [Context HookType HooksMap]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(defn- doc-list [& s]
  (with-meta [] {:doc (apply str s)}))

(s/defn ^:private empty-hooks :- HooksMap
  []
  {:before-create (doc-list "`before-create` hooks are triggered on"
                            " create routes before the entity is saved in the DB.")
   :after-create (doc-list "`after-create` hooks are called after an entity was created.")
   :before-update (doc-list "`before-update` hooks are triggered on"
                            " update routes before the entity is saved in the DB.")
   :after-update (doc-list "`after-update` hooks are called after an entity was updated.")
   :before-delete (doc-list "`before-delete` hooks are called before an entity is deleted.")
   :after-delete (doc-list "`after-delete` hooks are called after an entity is deleted.")
   :event (doc-list "`event` hooks are called with an event during any CRUD activity.")})

(s/defn reset-hooks! :- HooksMap
  [{:keys [hooks]} :- Context
   get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (reset! hooks
          (-> (empty-hooks)
              (event-hooks/register-hooks get-in-config))))

(s/defn add-hook! :- HooksMap
  "Add a `Hook` for the hook `hook-type`"
  [{:keys [hooks]} :- Context
   hook-type :- HookType
   hook :- (s/protocol prot/Hook)]
  (swap! hooks update hook-type conj hook))

(s/defn add-hooks! :- HooksMap
  "Add a list of `Hook` for the hook `hook-type`"
  [{:keys [hooks]} :- Context
   hook-type :- HookType
   hook-list :- [(s/protocol prot/Hook)]]
  (swap! hooks update hook-type into hook-list))

(s/defn init-hooks! :- HooksMap
  "Initialize all hooks"
  [{:keys [hooks]} :- Context]
  (doseq [hook-list (vals @hooks)
          hook hook-list]
    (prot/init hook))
  @hooks)

(s/defn destroy-hooks!
  "Should call all destructor for each hook in reverse order."
  [{:keys [hooks]} :- Context]
  (doseq [hook-list (vals @hooks)
          hook (reverse hook-list)]
    (prot/destroy hook)))

(s/defn apply-hooks
  "Apply the registered hooks for a given hook-type to the passed in data.
   Data may be an entity (or an event) and a previous entity.  Accepts
  read-only?, in which case the result of the hooks do not change the result.
  In any hook that returns nil, the result is ignored and the input entity is kept."
  [{:keys [hooks]} :- Context
   {:keys [hook-type
           entity
           prev-entity
           read-only?]} :- ApplyHooksOptions]
  (loop [[hook & more-hooks :as hooks] (get @hooks hook-type)
         result entity]
    (if (empty? hooks)
      result
      (let [handle-result (prot/handle hook result prev-entity)]
        (if (or read-only?
                (nil? handle-result))
          (recur more-hooks result)
          (recur more-hooks handle-result))))))

(s/defn apply-event-hooks
  [context :- Context
   event]
  (apply-hooks context
               {:hook-type :event
                :entity event
                :read-only? true}))

(s/defn shutdown!
  "Normally this should not be called directly since init! registers a
  shutdown hook"
  [context :- Context]
  (destroy-hooks! context))

(s/defn init :- Context
  [context]
  (assoc context :hooks (atom (empty-hooks))))

(s/defn start :- Context
  "Initialize all hooks"
  [{:keys [hooks] :as context} :- Context
   get-in-config :- (st/get-in ConfigServiceFns [:get-in-config])]
  (reset-hooks! context get-in-config)
  (init-hooks! context)
  (log/info "Hooks Initialized: " (pr-str @hooks))
  context)

(s/defn stop
  "Should call all destructor for each hook in reverse order."
  [context :- Context]
  (shutdown! context)
  (dissoc context :hooks))
