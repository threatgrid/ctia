(ns ctia.store
  (:require
   [clojure.tools.logging :as log]))

(defprotocol IStore
  (create-record [this new-records ident params])
  (read-record [this id ident params])
  (update-record [this id record ident])
  (delete-record [this id ident])
  (list-records [this filtermap ident params]))

(defprotocol IJudgementStore
  (calculate-verdict [this observable ident])
  (list-judgements-by-observable [this observable ident params])
  (add-indicator-to-judgement [this judgement-id indicator-relationship ident]))

(defprotocol ISightingStore
  (list-sightings-by-observables [this observable ident params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IEventStore
  (create-events [this new-events])
  (list-events [this filtermap ident params]))

(defprotocol IQueryStringSearchableStore
  (query-string-search [this query filtermap ident params]))

(def empty-stores
  {:judgement []
   :indicator []
   :feedback []
   :campaign []
   :actor []
   :coa []
   :data-table []
   :sighting []
   :incident []
   :relationship []
   :identity []
   :attack-pattern []
   :malware []
   :tool []
   :event []
   :investigation []
   :casebook []
   :vulnerability []
   :weakness []})

(defonce stores (atom empty-stores))

(defn write-store [store write-fn & args]
  (first (doall (map #(apply write-fn % args) (store @stores)))))

(defn read-store [store read-fn & args]
  (apply read-fn (first (get @stores store)) args))

(defn query-string-search-store [store read-fn & args]
  (log/debug "query-string-search-store args: " store read-fn args)
  (apply read-fn (first (get @stores store)) args))

(def read-fn read-record)
(def create-fn create-record)
(def list-fn list-records)
