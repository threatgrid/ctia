(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-momo.lib.es
             [document :as es-doc]
             [index :as es-index]]
            [clojure.java.io :as io]
            [ctia
             [store :as store]]
            [clojure.walk :as walk]
            [ctia.stores.es
             [init :as es-init]
             [store :as es-store]
             [schemas :refer [ESConnServices]]]
            [ctia.test-helpers.core :as h]
            [schema.core :as s]))

(s/defn app->ESConnServices
  :- ESConnServices
  "Create a ESConnServices map with an app"
  [app]
  (let [get-in-config (h/current-get-in-config-fn app)]
    {:ConfigService {:get-in-config get-in-config}}))

(s/defn ->ESConnServices
  :- ESConnServices
  "Create a ESConnServices map without an app"
  []
  (let [get-in-config (h/build-get-in-config-fn)]
    {:ConfigService {:get-in-config get-in-config}}))

(defn refresh-indices [entity get-in-config]
  (let [{:keys [host port]}
        (es-init/get-store-properties entity get-in-config)]
    (http/post (format "http://%s:%s/_refresh" host port))))

(defn delete-store-indexes
  ([restore-conn?]
   (let [app (h/get-current-app)
         {:keys [get-in-config]} (h/get-service-map app :ConfigService)
         {:keys [all-stores]} (h/get-service-map app :StoreService)]
     (delete-store-indexes
       restore-conn?
       all-stores
       get-in-config)))
  ([restore-conn? all-stores get-in-config]
   (doseq [store-impls (vals (all-stores))
           {:keys [state]} store-impls]
     (es-store/delete-state-indexes state)
     (when restore-conn?
       (es-init/init-es-conn!
         (es-init/get-store-properties (get-in state [:props :entity])
                                       get-in-config)
         {:ConfigService {:get-in-config get-in-config}})))))

(defn fixture-delete-store-indexes
  "walk through all the es stores delete each store indexes"
  [t]
  (let [app (h/get-current-app)
        {:keys [get-in-config]} (h/get-service-map app :ConfigService)
        {:keys [all-stores]} (h/get-service-map app :StoreService)]
    (delete-store-indexes true all-stores get-in-config)
    (try
      (t)
      (finally
        (delete-store-indexes false all-stores get-in-config)))))

(s/defn purge-index [entity
                     {{:keys [get-in-config]} :ConfigService
                      :as services} :- ESConnServices]
  (let [{:keys [conn index]} (es-init/init-store-conn
                              (es-init/get-store-properties entity get-in-config)
                              services)]
    (when conn
      (es-index/delete! conn (str index "*")))))

(defn fixture-purge-event-indexes
  "walk through all producers and delete their index"
  [t]
  (let [app (h/get-current-app)
        services (app->ESConnServices app)]
    (purge-index :event services)
    (try
      (t)
      (finally
        (purge-index :event services)))))

(defn purge-indexes [all-stores get-in-config]
  (doseq [entity (keys (all-stores))]
    (purge-index entity get-in-config)))

(defn fixture-purge-indexes
  "walk through all producers and delete their index"
  [t]
  (let [app (h/get-current-app)
        {:keys [get-in-config]} (h/get-service-map app :ConfigService)
        {:keys [all-stores]} (h/get-service-map app :StoreService)]
    (purge-indexes all-stores get-in-config)
    (try
      (t)
      (finally (purge-indexes all-stores get-in-config)))))

(defn fixture-properties:es-store [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.es.default.shards" 5
                      "ctia.store.es.default.replicas" 1
                      "ctia.store.es.default.refresh" "true"
                      "ctia.store.es.default.refresh_interval" "1s"
                      "ctia.store.es.default.port" "9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.default.default_operator" "AND"
                      "ctia.store.es.default.aliased" true
                      "ctia.store.es.default.rollover.max_docs" 50

                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.es.actor.default_operator" "OR"
                      "ctia.store.es.asset.indexname" "ctia_assets"
                      "ctia.store.es.asset-mapping.indexname" "ctia_asset_mapping"
                      "ctia.store.es.asset-properties.indexname" "ctia_asset_properties"
                      "ctia.store.es.attack-pattern.indexname" "ctia_attack_pattern"
                      "ctia.store.es.campaign.indexname" "ctia_campaign"
                      "ctia.store.es.coa.indexname" "ctia_coa"
                      "ctia.store.es.event.indexname" "ctia_event"
                      "ctia.store.es.data-table.indexname" "ctia_data-table"
                      "ctia.store.es.feedback.indexname" "ctia_feedback"
                      "ctia.store.es.identity.indexname" "ctia_identities"
                      "ctia.store.es.incident.indexname" "ctia_incident"
                      "ctia.store.es.indicator.indexname" "ctia_indicator"
                      "ctia.store.es.investigation.indexname" "ctia_investigation"
                      "ctia.store.es.judgement.indexname" "ctia_judgement"
                      "ctia.store.es.malware.indexname" "ctia_malware"
                      "ctia.store.es.relationship.indexname" "ctia_relationship"
                      "ctia.store.es.casebook.indexname" "ctia_casebook"
                      "ctia.store.es.sighting.indexname" "ctia_sighting"
                      "ctia.store.es.identity-assertion.indexname" "ctia_identity_assertion"
                      "ctia.store.es.target-record.indexname" "ctia_target_record"
                      "ctia.store.es.tool.indexname" "ctia_tool"
                      "ctia.store.es.vulnerability.indexname" "ctia_vulnerability"
                      "ctia.store.es.weakness.indexname" "ctia_weakness"

                      "ctia.store.actor" "es"
                      "ctia.store.asset" "es"
                      "ctia.store.asset-mapping" "es"
                      "ctia.store.asset-properties" "es"
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
                      "ctia.store.target-record" "es"
                      "ctia.store.tool" "es"
                      "ctia.store.vulnerability" "es"
                      "ctia.store.weakness" "es"
                      "ctia.store.bulk-refresh" "true"

                      "ctia.migration.store.es.migration.indexname" "ctia_migration"
                      "ctia.migration.store.es.default.rollover.max_docs" 50
                      "ctia.migration.store.es.event.rollover.max_docs" 1000]
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
