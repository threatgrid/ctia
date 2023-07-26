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

(defn find-minimal-stores []
  (let [;;remove me
        entity-crud-route-tests #(take 1 (entity-crud-route-tests))]
    (into {}
          (map (fn [[k tst]]
                 (some (fn [i]
                         (prn "i" i)
                         (some (fn [enabled-stores]
                                 (when (enabled-stores k) ;;current entity must always be enabled
                                   (prn "enabled-stores" enabled-stores)
                                   (let [res (th/with-enabled-stores enabled-stores
                                               #(t/run-test-var tst))
                                         _ (prn "res" res)]
                                     (when (t/successful? res)
                                       [k enabled-stores]))))
                               (map set (comb/combinations possible-stores-to-enable i))))
                       (range 1 (inc (count possible-stores-to-enable))))))
          (entity-crud-route-tests))))

(comment
  (find-minimal-stores)
  )
