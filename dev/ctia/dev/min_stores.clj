(ns ctia.dev.min-stores
  (:require [clojure.test :as t]
            [clojure.math.combinatorics :as comb]
            [ctia.store :as store]
            [ctia.test-helpers.core :as th]))

(def infer-these-stores
  #{;:actor
    :asset
    :asset-mapping
    :asset-properties
    :attack-pattern
    :campaign
    :casebook
    :coa
    :data-table
    :event
    :feed
    :feedback
    ;:identity
    :identity-assertion
    ;:incident
    :indicator
    :investigation
    :judgement
    :malware
    :note
    :relationship
    :sighting
    :target-record
    :tool
    :vulnerability
    :weakness})

(def possible-stores-to-enable
  (-> store/known-stores
      (disj :events :identity)
      sort
      vec))

(defn entity-test-namespaces []
  (into {}
        (map #(let [ns (the-ns (symbol (str "ctia.entity." (name %) "-test")))]
                {% (ns-name ns)}))
        infer-these-stores))

(defn entity-crud-route-tests []
  (into {}
        (map (fn [[k v]]
               (let [ent (name k)
                     v (some #(find-var (symbol (name v) %))
                             [(str "test-" ent "-routes")
                              (str ent "-routes-test")
                              (str "test-" ent "-crud-routes")])]
                 (assert (var? v) k)
                 [k v])))
        (entity-test-namespaces)))

(defn log [ent & args]
  (assert (infer-these-stores ent))
  (let [msg (with-out-str (apply prn ent (java.util.Date.) args))]
    (spit (str "min-stores-" (name ent) ".txt")
          msg
          :append true)
    (spit "min-stores-all.txt" msg :append true)))

(defn find-minimal-stores []
  (into {}
        (pmap (fn [[k tst]]
                (some (fn [i]
                        (log k "i" i)
                        (some (fn [enabled-stores]
                                (when (enabled-stores k) ;;current entity must always be enabled
                                  (log k "enabled-stores" enabled-stores)
                                  (let [res (let [out (new java.io.StringWriter)
                                                  err (new java.io.StringWriter)]
                                              (binding [*out* out
                                                        *err* err]
                                                (let [res (th/with-enabled-stores enabled-stores
                                                            #(t/run-test-var tst))]
                                                  (log k "out" out)
                                                  (log k "err" err)
                                                  res)))
                                        _ (log k "res" res)]
                                    (if (t/successful? res)
                                      (do (log k "GOOD" enabled-stores)
                                          [k enabled-stores])
                                      (do (log k "BAD" enabled-stores)
                                          nil)))))
                              (map set (comb/combinations possible-stores-to-enable i))))
                      (range 1 (inc (count possible-stores-to-enable)))))
              (entity-crud-route-tests))))

(comment
  (find-minimal-stores)
  )

; lein run -m ctia.dev.min-stores/-main &> LOG.txt
; $ tail -f min-stores-all.txt
(defn -main [& arg]
  (require 'user)
  ((requiring-resolve 'clojure.tools.namespace.repl/refresh))
  (let [result (find-minimal-stores)]
    (prn "RESULT" result)
    (let [msg (with-out-str (prn "FINAL RESULT" result))]
      (spit "RESULT.txt" msg :append true)
      (spit "min-stores-all.txt" msg :append true))
    (System/exit 0)))
