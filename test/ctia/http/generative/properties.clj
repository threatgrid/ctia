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
            [ctim.schemas
             [actor :refer [NewActor]]
             [campaign :refer [NewCampaign]]
             [coa :refer [NewCOA]]
             [exploit-target :refer [NewExploitTarget]]
             [feedback :refer [NewFeedback]]
             [incident :refer [NewIncident]]
             [indicator :refer [NewIndicator]]
             [judgement :refer [NewJudgement] :as csj]
             [relationship :refer [NewRelationship]]
             [sighting :refer [NewSighting]]
             [ttp :refer [NewTTP]]]
            [flanders
             [spec :as fs]
             [utils :as fu]]))

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

(doseq [[entity kw-ns] [[NewActor "max-new-actor"]
                        [NewCampaign "max-new-campaign"]
                        [NewCOA "max-new-coa"]
                        [NewExploitTarget "max-new-exploit-target"]
                        [NewFeedback "max-new-feedback"]
                        [NewIncident "max-new-incident"]
                        [NewIndicator "max-new-indicator"]
                        [NewJudgement "max-new-judgement"]
                        [NewRelationship "max-new-relationship"]
                        [NewSighting "max-new-sighting"]
                        [NewTTP "max-new-ttp"]]]
  (fs/->spec (fu/require-all entity)
             kw-ns))

(defn spec-gen [kw-ns]
  (tcg/fmap #(dissoc % :id)
            (cs/gen (keyword kw-ns "map"))))

(def api-for-actor-routes
  (api-for-route 'actor
                 (spec-gen "max-new-actor")))

(def api-for-campaign-routes
  (api-for-route 'campaign
                 (spec-gen "max-new-campaign")))

(def api-for-coa-routes
  (api-for-route 'coa
                 (spec-gen "max-new-coa")))

(def api-for-exploit-target-routes
  (api-for-route 'exploit-target
                 (spec-gen "max-new-exploit-target")))

(def api-for-indicator-routes
  (api-for-route 'indicator
                 (spec-gen "max-new-indicator")))

(def api-for-feedback-routes
  (api-for-route 'feedback
                 (spec-gen "max-new-feedback")))

(def api-for-incident-routes
  (api-for-route 'incident
                 (spec-gen "max-new-incident")))

(def api-for-judgement-routes
  (api-for-route 'judgement
                 (tcg/fmap csj/fix-disposition
                           (spec-gen "max-new-judgement"))))

(def api-for-relationship-routes
  (api-for-route 'relationship
                 (spec-gen "max-new-relationship")))

(def api-for-sighting-routes
  (api-for-route 'sighting
                 (spec-gen "max-new-sighting")))

(def api-for-ttp-routes
  (api-for-route 'ttp
                 (spec-gen "max-new-ttp")))
