(ns ctia.tk
  "Diagnostic wrappers for trapperkeeper services. Set tk-options
  to configure."
  (:require [clojure.set :as set]
            [puppetlabs.trapperkeeper.services :refer [service
                                                       name-with-attributes
                                                       ServiceDefinition
                                                       service-def-id
                                                       service-map]
             :as services])
  (:import [clojure.lang ExceptionInfo]))

(def tk-options
  "Allowed values:

  #{:off}   - Use vanilla defservice
  #{:log-first-def} - print diagnostic information when defservice is first evaluated
  #{:stale-error} - Throw an exception on stale service usage
  #{:stale-error :no-redef-error} - Throw an exception on stale service usage and service redefinition
  #{:stale-debug} - Print diagnostic information on stale service usage
  #{:stale-debug :no-redef-debug} - Print diagnostic information on stale service usage and service redefinition"
  #{:off #_:stale-error #_:no-redef-debug #_:log-first-def})

(defn- find-prot-qsym
  [forms]
  {:post [(qualified-symbol? %)]}
  (let [maybe-sym (first forms)
        qsym (when (symbol? maybe-sym)
               (let [res (resolve maybe-sym)]
                 (if (var? res)
                   (do (assert (some-> res deref :on-interface class?)
                               res)
                       (symbol res))
                   (throw (ex-info "Must provide protocol for service in CTIA"
                                   {})))))]
    qsym))

  
;; Only uses public trapperkeeper API's, but if ServiceDefinition
;; gains new methods, will need to add them in the reified wrapper.
(defmacro defservice
  "A variant of Trapperkeeper's defservice that enforces
  a provided protocol and checks for stale usage of the ServiceDefinition."
  [svc-name & forms]
  {:pre [(simple-symbol? svc-name)]}
  (condp #(contains? %2 %1) tk-options
    :off
    `(services/defservice ~svc-name ~@forms)
    (let [service-sym      (symbol (name (ns-name *ns*)) (name svc-name))
          [svc-name forms] (name-with-attributes svc-name forms)
          ;; just extract the implemented protocol, we're going to pass `forms`
          ;; as-is to trapperkeeper.
          prot-qsym (find-prot-qsym forms)
          svc-qsym (symbol (str (ns-name *ns*))
                           (str svc-name))]
      `(do
         (declare ~svc-name)
         (when (:log-first-def tk-options)
           (when (not (bound? (var ~svc-name)))
             (prn (try (throw (ex-info ~(str "First definition of "
                                             svc-qsym)
                                       {}))
                       (catch ExceptionInfo e# e#)))))
         (when (seq (set/intersection #{:no-redef-debug :no-redef-error}
                                      tk-options))
           (when (bound? (var ~svc-name))
             (let [s# ~(str "Redefinition of " svc-qsym " detected")
                   m# {:existing-service ((juxt identity hash) @(var ~svc-name))}]
               (condp #(contains? %2 %1) tk-options
                 :no-redef-error (throw (ex-info s# m#))
                 :no-redef-debug (prn 'DEBUG s# m#
                                      (try (throw (ex-info s# m#))
                                           (catch ExceptionInfo e# e#)))))))
         (def ~svc-name
           (let [;; we will delegate all non-diagnostic service behavior to
                 ;; this instance created by regular old TK `service`.
                 inner-svc# (service {:service-symbol ~service-sym} ~@forms)
                 ;; return the "current" definition of this service based on the
                 ;; the current namespace graph
                 get-latest-svc# (fn []
                                   {:post [(satisfies? ServiceDefinition ~'%)]}
                                   (let [v# (find-var '~svc-qsym)
                                         _# (assert (var? v#)
                                                    (pr-str v#))
                                         latest-svc# @v#
                                         _# (assert (satisfies? ServiceDefinition latest-svc#)
                                                    (pr-str latest-svc#))]
                                     latest-svc#))
                 get-latest-interface# (fn []
                                         {:post [(class? ~'%)
                                                 (.isInterface ^Class ~'%)]}
                                         (some-> (find-var '~prot-qsym)
                                                 deref
                                                 :on-interface))
                 original-interface# (get-latest-interface#)
                 assert-latest-service# (fn [this#]
                                          (let [latest-svc# (get-latest-svc#)
                                                latest-interface# (get-latest-interface#)
                                                outdated-interface?# (not
                                                                       (identical?
                                                                         original-interface#
                                                                         latest-interface#))
                                                outdated-svc?# (not
                                                                 (identical?
                                                                   this#
                                                                   latest-svc#))
                                                diag-svc# (juxt identity hash)
                                                diag-interface# (juxt identity hash)
                                                data# {:services {:old (diag-svc# this#)
                                                                  :new (diag-svc# latest-svc#)}
                                                       :interfaces {:old (diag-interface#
                                                                           original-interface#)
                                                                    :new (diag-interface#
                                                                           latest-interface#)}
                                                       :outdated-interface? outdated-interface?#
                                                       :outdated-svc# outdated-svc?#}
                                                report# (fn [s# m#]
                                                          (condp #(contains? %2 %1) tk-options
                                                            :stale-error (throw (ex-info s# m#))
                                                            :stale-debug
                                                            (prn 'DEBUG s# m#
                                                                 (try (throw (ex-info s# m#))
                                                                      (catch ExceptionInfo e# e#)))))]
                                            (when outdated-svc?#
                                              (report# ~(str "Detected outdated ServiceDefinition for "
                                                             svc-qsym)
                                                       data#))
                                            (when outdated-interface?#
                                              (report# ~(str "Detected outdated use of protocol "
                                                             prot-qsym
                                                             " in service "
                                                             svc-qsym)
                                                       data#))))]
             (reify ServiceDefinition
               (service-def-id [this#]
                 (assert-latest-service# this#)
                 (service-def-id inner-svc#))
               (service-map [this#]
                 (assert-latest-service# this#)
                 (service-map inner-svc#)))))))))
