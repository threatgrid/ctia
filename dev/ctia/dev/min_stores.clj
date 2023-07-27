(ns ctia.dev.min-stores
  (:require [clojure.test :as t]
            [clojure.set :as set]
            [clojure.math.combinatorics :as comb]
            [ctia.store :as store]
            [ctia.test-helpers.core :as th]))

;:feed #inst "2023-07-26T22:19:11.381-00:00" :actor "DISABLEABLE" #{:feed :tool :relationship :judgement :attack-pattern :incident :indicator :casebook :malware}
;:feedback #inst "2023-07-26T22:22:08.958-00:00" :actor "DISABLEABLE" #{:feedback :tool :attack-pattern :incident :casebook :malware}
;:investigation #inst "2023-07-26T22:25:28.435-00:00" :actor "DISABLEABLE" #{:investigation :tool :attack-pattern :incident :casebook :malware}
;:asset-mapping #inst "2023-07-26T22:29:49.994-00:00" :actor "DISABLEABLE" #{:asset-mapping :tool :attack-pattern :incident :casebook :malware}
;:data-table #inst "2023-07-26T22:34:05.949-00:00" :actor "DISABLEABLE" #{:data-table :tool :attack-pattern :incident :casebook :malware}
;:tool #inst "2023-07-26T22:39:12.336-00:00" :actor "DISABLEABLE" #{:tool :attack-pattern :incident :casebook :malware}
;:relationship #inst "2023-07-26T22:44:35.583-00:00" :actor "DISABLEABLE" #{:tool :relationship :attack-pattern :incident :asset-properties :casebook :malware}
;:vulnerability #inst "2023-07-26T22:50:31.079-00:00" :actor "DISABLEABLE" #{:tool :vulnerability :attack-pattern :incident :casebook :malware}
;:judgement #inst "2023-07-26T22:57:31.054-00:00" :actor "DISABLEABLE" #{:tool :judgement :attack-pattern :incident :casebook :malware}
;:target-record #inst "2023-07-26T23:04:39.549-00:00" :actor "DISABLEABLE" #{:tool :target-record :attack-pattern :incident :casebook :malware}
;:weakness #inst "2023-07-26T23:12:08.965-00:00" :actor "DISABLEABLE" #{:tool :weakness :attack-pattern :incident :casebook :malware}
;:note #inst "2023-07-26T23:19:56.926-00:00" :actor "DISABLEABLE" #{:tool :note :attack-pattern :incident :casebook :malware}
;:coa #inst "2023-07-26T23:28:40.756-00:00" :actor "DISABLEABLE" #{:tool :coa :attack-pattern :incident :campaign :casebook :malware}
;:attack-pattern #inst "2023-07-26T23:38:25.019-00:00" :actor "DISABLEABLE" #{:tool :attack-pattern :incident :casebook :malware}
;:indicator #inst "2023-07-26T23:46:30.765-00:00" :actor "DISABLEABLE" #{:indicator}
;:campaign #inst "2023-07-26T23:49:27.586-00:00" :actor "DISABLEABLE" #{:tool :attack-pattern :incident :campaign :casebook :malware}
;:asset-properties #inst "2023-07-26T23:53:10.630-00:00" :actor "DISABLEABLE" #{:tool :attack-pattern :incident :asset-properties :casebook :malware}
;:sighting #inst "2023-07-27T00:01:00.082-00:00" :actor "DISABLEABLE" #{:tool :attack-pattern :incident :sighting :casebook :malware}



(def infer-these-stores
  #{;:actor
    :asset
    ;:asset-mapping
    ;:asset-properties
    ;:attack-pattern
    ;:campaign
    :casebook
    ;:coa
    ;:data-table
    ;:event
    ;:feed
    ;:feedback
    ;:identity
    :identity-assertion
    ;:incident
    ;:indicator
    ;:investigation
    ;:judgement
    :malware
    ;:note
    ;:relationship
    ;:sighting
    ;:target-record
    ;:tool
    ;:vulnerability
    ;:weakness
    })

(def possible-stores-to-enable
  (-> store/known-stores
      (disj :events :identity)))

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

(defn find-minimal-stores-for-test [[ent tst]]
  (let [disableable-entities
        (reduce (fn [disableable-entities disable-entity]
                  (let [enabled-stores (-> possible-stores-to-enable 
                                           (set/difference disableable-entities)
                                           (disj disable-entity))
                        _ (log k disable-entity "enabled-stores" enabled-stores)
                        res (th/with-enabled-stores enabled-stores
                              #(t/run-test-var tst))
                        good? (t/successful? res)]
                    (log k disable-entity (if good? "DISABLEABLE" "ESSENTIAL") enabled-stores)
                    (cond-> disableable-entities
                      good? (conj disable-entity))))
                #{} (disj possible-stores-to-enable k))]
    [k (set/difference possible-stores-to-enable disableable-entities)]))

(defn find-minimal-stores []
  (into {} (map find-minimal-stores-for-test)
        (entity-crud-route-tests)))

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
