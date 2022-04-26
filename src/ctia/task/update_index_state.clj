(ns ctia.task.update-index-state
  (:require [ctia.init :as init]
            [ctia.properties :as p]
            [puppetlabs.trapperkeeper.internal :as internal]))

(defn system-exit-error
  []
  (log/error (str "IGNORE THIS LOG UNTIL MIGRATION -- "
                  "CTIA tried to start with an invalid configuration: \n"
                  "- invalid mapping\n"
                  "- ambiguous index names"))
  (System/exit 1))

(s/defn update-mappings!
  [{:keys [conn index]
    {:keys [mappings]} :config} :- ESConnState]
  (let [[entity-type type-mappings] (when (= (:version conn) 5)
                                      (first mappings))
        update-body (or type-mappings mappings)]
    (try
      (log/info "updating mapping: " index)
      (ductile.index/update-mappings! conn
                                      index
                                      entity-type
                                      update-body)
      (catch clojure.lang.ExceptionInfo e
        (log/error "cannot update mapping. You probably tried to update the mapping of an existing field. It's only possible to add new field to existing mappings. If you need to modify the type of a field in an existing index, you must perform a migration"
                   (assoc (ex-data e)
                          :conn conn
                          :mappings mappings))
        (system-exit-error)))))

(s/defn refresh-mappings!
  [{:keys [conn index]
    {:keys [mappings]} :config} :- ESConnState]
  (try
    (log/info "refreshing mapping: " index)
    (ductile.document/update-by-query conn
                                      [index] {}
                                      {:refresh "true"
                                       :conflicts "proceed"
                                       :wait_for_completion false})
    (catch clojure.lang.ExceptionInfo e
      (log/error "Cannot refresh mapping."
                 (assoc (ex-data e)
                        :conn conn
                        :mappings mappings))
      (system-exit-error))))

(s/defn update-settings!
  "read store properties of given stores and update indices settings."
  [{:keys [conn index]
    {:keys [settings]} :config} :- ESConnState]
  (try
    (->> {:index (select-keys settings [:refresh_interval :number_of_replicas])}
         (ductile.index/update-settings! conn index))
    (log/info "updated settings: " index)
    (catch clojure.lang.ExceptionInfo e
      (log/warn "could not update settings on that store"
                (pr-str (ex-data e))))))

(defn update-index-state
  [{{update-mappings?  :update-mappings
     update-settings?  :update-settings
     refresh-mappings? :refresh-mappings}
    :props
    :as conn-state}]
  (when update-mappings?
    (update-mappings! conn-state)
    ;; template update must be after update-mapping
    ;; if it fails a System/exit is triggered because
    ;; this means that the mapping in invalid and thus
    ;; must not be propagated to the template that would accept it
    (upsert-template! conn-state)
    (when refresh-mappings?
      (refresh-mappings! conn-state)))
  (when update-settings?
    (update-settings! conn-state)))

(defn do-task []
  (try (-> {:config (assoc-in (p/build-init-config)
                              [:ctia :task :ctia.task.update-index-state]
                              true)}
           init/start-ctia!
           internal/shutdown ;; returns number of exceptions thrown
           count
           (min 1)) ;; exit 1 if exceptions, otherwise 0
       (catch Throwable e
         (prn e)
         1)))

(defn -main [& args]
  (assert (empty? args) "No arguments supported by ctia.task.update-mappings")
  (System/exit (do-task)))
