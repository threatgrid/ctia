(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-momo.lib.es.index :as es-index]
            [clojure.pprint :refer [pprint]]
            [ctia
             [properties :refer [properties]]
             [store :as store]]
            [ctia.stores.es
             [init :as es-init]
             [store :as es-store]]
            [ctia.test-helpers.core :as h]))

(defn fixture-delete-store-indexes
  "walk through all the es stores delete each store indexes"
  [t]
  (doseq [store-impls (vals @store/stores)
          {:keys [state]} store-impls]
    (es-store/delete-state-indexes state))
  (t))

(defn purge-event-indexes []
  (let [{:keys [conn index]} (es-init/init-store-conn
                              (es-init/get-store-properties :event))]
    (when conn
      (es-index/delete! conn (str index "*")))))

(defn fixture-purge-event-indexes
  "walk through all producers and delete their index"
  [t]
  (purge-event-indexes)
  (t)
  (purge-event-indexes))

(defn fixture-properties:es-store [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.es.default.shards" 1
                      "ctia.store.es.default.replicas" 1
                      "ctia.store.es.default.refresh" "true"
                      "ctia.store.es.default.port" "9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.actor" "es"
                      "ctia.store.attack-pattern" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.data-table" "es"
                      "ctia.store.event" "es"
                      "ctia.store.exploit-target" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.investigation" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.malware" "es"
                      "ctia.store.relationship" "es"
                      "ctia.store.scratchpad" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.tool" "es"]
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

(defn- url-for-type [t]
  (assert (keyword? type) "Type must be a keyword")
  (let [{:keys [indexname host port]}
        (-> @ctia.store/stores
            t
            first
            :state
            :props)]
    (assert (seq host) "Missing host")
    (assert (integer? port) "Missing port")
    (assert (seq indexname) "Missing index-name")
    (str "http://" host ":" port "/" indexname "/" (name t) "/")))

(defn post-to-es [obj]
  (let [{:keys [status] :as response}
        (http/post
         (url-for-type (-> obj :type keyword))
         {:as :json
          :content-type :json
          :throw-exceptions false
          :body (json/generate-string obj)})]
    (when (not= 201 status)
      (println "Post to ES failed.\nWrong HTTP status code: " status)
      (pprint response)
      (throw (AssertionError. "POST to ES failed")))))

(defn post-all-to-es [objects]
  (run! post-to-es objects))
