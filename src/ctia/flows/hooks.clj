(ns ctia.flows.hooks
  "Handle hooks ([Cf. #159](https://github.com/threatgrid/ctia/issues/159))."
  (:require [clojure.core.async :as a]
            [ctia.flows
             [autoload :as auto-hooks]
             [hooks-pipe :as fhp]]
            [ctia.flows.hooks.after-hooks :as after-hooks]
            [ctia.flows.hooks.event-hooks :as event-hooks]
            [ctia.flows
             [hooks-pipe :as fhp]
             [hook-protocol
              :refer [Hook]
              :as prot]]
            [ctia.lib.async :as la]
            [ctia.shutdown :as shutdown]))

(defn- doc-list [& s]
  (with-meta [] {:doc (apply str s)}))

(def empty-hooks
  {:before-create (doc-list "`before-create` hooks are triggered on create "
                            "routes before the entity is saved in the DB.")

   :after-create (doc-list "`after-create` hooks are called after an entity was"
                           " created.")

   :before-update (doc-list "`before-update` hooks are triggered on"
                            " update routes before the entity is saved in the "
                            "DB.")

   :after-update (doc-list "`after-update` hooks are called after an entity was "
                           "updated.")

   :before-delete (doc-list "`before-delete` hooks are called before an entity "
                            "is deleted.")

   :after-delete (doc-list "`after-delete` hooks are called after an entity is "
                           "deleted.")

   :event (doc-list "`event` hooks are called with an event during any CRUD "
                    "activity.")})

(defonce hooks (atom empty-hooks))

(defn reset-hooks! []
  (reset! hooks
          (-> empty-hooks
              after-hooks/register-hooks
              event-hooks/register-hooks
              auto-hooks/register-hooks)))

(defn add-hook!
  "Add a `Hook` for the hook `hook-type`"
  [hook-type hook]
  (swap! hooks update hook-type conj hook))

(defn add-hooks!
  "Add a list of `Hook` for the hook `hook-type`"
  [hook-type hook-list]
  (swap! hooks update hook-type into hook-list))

(defn init-hooks!
  "Initialize all hooks"
  []
  (doseq [hook-list (vals @hooks)
          hook hook-list]
    (prot/init hook))
  @hooks)

(defn destroy-hooks!
  "Should call all destructors for each hook in reverse order."
  []
  (doseq [hook-list (vals @hooks)
          hook (reverse hook-list)]
    (prot/destroy hook)))

(defn apply-hooks
  "Apply the registered hooks for a given hook-type to the passed in data.

  Data may be an entity (or an event) and a previous entity (the
  entity is read from the passed in entity-chan channel).  Accepts
  read-only?, in which case the result of the hooks do not change the
  result.  If any hook returns nil, the result is ignored and the
  previous result is preserved.

  Application is done on one of the hooks-pipe threads (asynchronous)."
  [& {:keys [hook-type
             entity-chan
             prev-entity
             read-only?] :as args}]
  (if-let [hooks (seq (get @hooks hook-type))]
    (fhp/apply-hooks {:entity-chan entity-chan
                      :hooks hooks
                      :prev-entity prev-entity
                      :read-only? read-only?})
    entity-chan))

(defn apply-event-hooks [event-chan]
  (apply-hooks :hook-type :event
               :entity-chan event-chan
               :read-only? true))

(defn shutdown!
  "Normally this should not be called directly since init! registers a
  shutdown hook"
  []
  (destroy-hooks!))

(defn init! []
  (reset-hooks!)
  (init-hooks!)
  (fhp/init-hooks-pipe!)
  (shutdown/register-hook! :flows.hooks shutdown!))
