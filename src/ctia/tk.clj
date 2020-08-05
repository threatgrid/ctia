(ns ctia.tk
  (:require [puppetlabs.trapperkeeper.services :refer [service name-with-attributes
                                                       ServiceDefinition
                                                       service-def-id
                                                       service-map]])
  (:import [clojure.lang IExceptionInfo]))

(def strictness
  "Allowed values:

  :error - Throw an exception on stale service usage
  :debug - Print diagnostic information on stale service usage"
  :error)

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

(defmacro defservice
  "A variant of Trapperkeeper's defservice that enforces
  a provided protocol and checks for stale usage."
  [svc-name & forms]
  {:pre [(simple-symbol? svc-name)]}
  (let [service-sym      (symbol (name (ns-name *ns*)) (name svc-name))
        [svc-name forms] (name-with-attributes svc-name forms)
        ;; just extract the implemented protocol, we're going to pass `forms`
        ;; as-is to trapperkeeper.
        prot-qsym (find-prot-qsym forms)
        svc-qsym (symbol (str (ns-name *ns*))
                         (str svc-name))]
    `(def ~svc-name
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
                                            outdated-interface?# (not= original-interface#
                                                                       latest-interface#)
                                            outdated-svc?# (not= this# latest-svc#)
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
                                                      (case strictness
                                                        :error (throw (ex-info s# m#))
                                                        :debug (prn s# m#
                                                                    (try (throw (ex-info s# m#))
                                                                         (catch IExceptionInfo e# e#)))))]
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
             (service-map inner-svc#)))))))
