(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [ctia.test-helpers.core :as helpers]
            [clj-momo.lib.es
             [document :as es-doc]
             [index :as es-index]]
            [clojure.java.io :as io]
            [ctia
             [store :as store]
             [store-service :as store-svc]]
            [clojure.walk :as walk]
            [ctia.stores.es
             [init :as es-init]
             [store :as es-store]]
            [ctia.test-helpers.core :as h]
            [puppetlabs.trapperkeeper.app :as app]))

(defn refresh-indices [entity]
  (let [{:keys [host port]}
        (es-init/get-store-properties entity)]
    (http/post (format "http://%s:%s/_refresh" host port))))

(defn delete-store-indexes
  ([restore-conn?]
   (let [app (h/get-current-app)
         store-svc (app/get-service app :StoreService)]
     (delete-store-indexes
       store-svc
       restore-conn?)))
  ([store-svc restore-conn?]
   (doseq [store-impls (vals @(store-svc/get-stores
                                store-svc))
           {:keys [state]} store-impls]
     (es-store/delete-state-indexes state)
     (when restore-conn?
       (es-init/init-es-conn!
         (es-init/get-store-properties (get-in state [:props :entity])))))))

(defn fixture-delete-store-indexes
  "walk through all the es stores delete each store indexes"
  [t]
  (let [app (h/get-current-app)
        store-svc (app/get-service app :StoreService)]
    (delete-store-indexes store-svc true)
    (t)
    (delete-store-indexes store-svc false)))

(defn purge-index [entity]
  (let [{:keys [conn index]} (es-init/init-store-conn
                              (es-init/get-store-properties entity))]
    (when conn
      (es-index/delete! conn (str index "*")))))

(defn fixture-purge-event-indexes
  "walk through all producers and delete their index"
  [t]
  (purge-index :event)
  (t)
  (purge-index :event))

(defn purge-indexes [store-svc]
  (doseq [entity (keys @(store-svc/get-stores store-svc))]
    (purge-index entity)))

(defn fixture-purge-indexes
  "walk through all producers and delete their index"
  [t]
  (let [app (helpers/get-current-app)
        store-svc (app/get-service app :StoreService)]
    (purge-indexes store-svc)
    (t)
    (purge-indexes store-svc)))

(defn fixture-properties:es-store [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.es.default.shards" 1
                      "ctia.store.es.default.replicas" 1
                      "ctia.store.es.default.refresh" "true"
                      "ctia.store.es.default.refresh_interval" "1s"
                      "ctia.store.es.default.port" "9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.default.default_operator" "AND"
                      "ctia.store.es.default.aliased" true
                      "ctia.store.es.default.rollover.max_docs" 50
                      "ctia.store.es.event.rollover.max_docs" 1000
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.es.actor.default_operator" "OR"
                      "ctia.store.es.migration.indexname" "ctia_migration"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.es.attack-pattern.indexname" "ctia_attack_pattern"
                      "ctia.store.es.campaign.indexname" "ctia_campaign"
                      "ctia.store.es.coa.indexname" "ctia_coa"
                      "ctia.store.es.event.indexname" "ctia_event"
                      "ctia.store.es.data-table.indexname" "ctia_data-table"
                      "ctia.store.es.feedback.indexname" "ctia_feedback"
                      "ctia.store.es.identity.indexname" "ctia_identity"
                      "ctia.store.es.incident.indexname" "ctia_incident"
                      "ctia.store.es.indicator.indexname" "ctia_indicator"
                      "ctia.store.es.investigation.indexname" "ctia_investigation"
                      "ctia.store.es.judgement.indexname" "ctia_judgement"
                      "ctia.store.es.malware.indexname" "ctia_malware"
                      "ctia.store.es.relationship.indexname" "ctia_relationship"
                      "ctia.store.es.casebook.indexname" "ctia_casebook"
                      "ctia.store.es.sighting.indexname" "ctia_sighting"
                      "ctia.store.es.identity-assertion.indexname" "ctia_identity_assertion"
                      "ctia.store.es.tool.indexname" "ctia_tool"
                      "ctia.store.es.vulnerability.indexname" "ctia_vulnerability"
                      "ctia.store.es.weakness.indexname" "ctia_weakness"
                      "ctia.store.actor" "es"
                      "ctia.store.attack-pattern" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.data-table" "es"
                      "ctia.store.event" "es"
                      "ctia.store.feed" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.investigation" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.malware" "es"
                      "ctia.store.relationship" "es"
                      "ctia.store.casebook" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.identity-assertion" "es" 
                      "ctia.store.tool" "es"
                      "ctia.store.vulnerability" "es"
                      "ctia.store.weakness" "es"
                      "ctia.store.bulk-refresh" "true"]
    (t)))

(defn fixture-properties:es-hook [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"]
    (t)))

(defn fixture-properties:es-hook:aliased-index [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "aliased-index"
                      "ctia.hook.es.slicing.granularity" "week"]
    (t)))

(defn str->doc
  [str-doc]
  (json/parse-string str-doc true))

(defn prepare-bulk-ops
  [str-doc]
  (let [{:keys [_type _id _index _source]} (str->doc str-doc)]
    (assoc _source
           :_type _type
           :_index _index
           :_id _id)))

(defn load-bulk
  ([es-conn docs] (load-bulk es-conn docs "true"))
  ([es-conn docs refresh?]
   (es-doc/bulk-create-doc es-conn
                           docs
                           refresh?)))

(defn load-file-bulk
  [es-conn filepath]
  (with-open [rdr (io/reader filepath)]
    (load-bulk es-conn
               (map prepare-bulk-ops
                    (line-seq rdr)))))

(defn make-cat-indices-url [host port]
  (format "http://%s:%s/_cat/indices?format=json&pretty=true" host port))

(defn get-cat-indices [host port]
  (let [url (make-cat-indices-url host
                                  port)
        {:keys [body]} (http/get url {:as :json})]
    (->> body
         (map (fn [{:keys [index]
                    :as entry}]
                {index (read-string
                        (:docs.count entry))}))
         (into {})
         walk/keywordize-keys)))
