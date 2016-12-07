(ns ctia.http.generative.properties
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :refer [common=]]
             [http :refer [encode]]]
            [clojure.spec :as cs]
            [clojure.test.check.generators :as tcg]
            [clojure.test.check.properties :refer [for-all]]
            [ctia.properties :refer [get-http-show]]
            [ctia.schemas.core] ;; for spec side-effects
            [ctia.test-helpers.core
             :refer [post get]]
            [ctim.domain.id :as id]
            [ctim.schemas.judgement :as csj]))

(defn api-for-route [model-type entity-gen]
  (for-all
    [new-entity entity-gen]
    (let [{post-status :status
           {id :id
            type :type
            :as post-entity} :parsed-body}
          (post (str "ctia/" (name model-type))
                :body new-entity)]

      (if (not= 201 post-status)
        (throw (ex-info "POST did not return status 201"
                        post-entity)))

      (let [url-id
            (-> (id/->id type id (get-http-show))
                :short-id
                encode)

            {get-status :status
             get-entity :parsed-body}
            (get (str "ctia/" type "/" url-id))]

        (if (not= 200 get-status)
          (throw (ex-info "GET did not return status 200"
                          get-entity)))

        (if-not (empty? (keys new-entity))
          (common= new-entity
                   post-entity
                   (dissoc get-entity :id))
          (common= post-entity
                   (dissoc get-entity :id)))))))

(defn spec-gen [kw-ns]
  (tcg/fmap #(dissoc % :id)
            (cs/gen (keyword kw-ns "map"))))

(def api-for-actor-routes
  (api-for-route 'actor
                 (spec-gen "new-actor")))

(def api-for-campaign-routes
  (api-for-route 'campaign
                 (spec-gen "new-campaign")))

(def api-for-coa-routes
  (api-for-route 'coa
                 (spec-gen "new-coa")))

(def api-for-exploit-target-routes
  (api-for-route 'exploit-target
                 (spec-gen "new-exploit-target")))

(def api-for-indicator-routes
  (api-for-route 'indicator
                 (spec-gen "new-indicator")))

(def api-for-feedback-routes
  (api-for-route 'feedback
                 (spec-gen "new-feedback")))

(def api-for-incident-routes
  (api-for-route 'incident
                 (spec-gen "new-incident")))

(def api-for-judgement-routes
  (api-for-route 'judgement
                 (tcg/fmap csj/fix-disposition
                           (spec-gen "new-judgement"))))

(def api-for-relationship-routes
  (api-for-route 'relationship
                 (spec-gen "new-relationship")))

(def api-for-sighting-routes
  (api-for-route 'sighting
                 (spec-gen "new-sighting")))

(def api-for-ttp-routes
  (api-for-route 'ttp
                 (spec-gen "new-ttp")))
