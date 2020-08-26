(ns ctia.http.generative.properties
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers
             [core :refer [common=]]
             [http :refer [encode]]]
            [clojure.spec.alpha :as cs]
            [clojure.test.check.generators :as tcg]
            [clojure.test.check.properties :refer [for-all]]
            [ctia.properties :refer [get-http-show]]
            [ctia.schemas.core] ;; for spec side-effects
            [ctia.test-helpers.core
             :as helpers :refer [post get]]
            [ctim.domain.id :as id]
            [ctim.schemas
             [actor :refer [NewActor]]
             [asset :refer [NewAsset]]
             [asset-mapping :refer [NewAssetMapping]]
             [asset-properties :refer [NewAssetProperties]]
             [attack-pattern :refer [NewAttackPattern]]
             [campaign :refer [NewCampaign]]
             [coa :refer [NewCOA]]
             [feedback :refer [NewFeedback]]
             [incident :refer [NewIncident]]
             [indicator :refer [NewIndicator]]
             [judgement :refer [NewJudgement] :as csj]
             [malware :refer [NewMalware]]
             [relationship :refer [NewRelationship]]
             [casebook :refer [NewCasebook]]
             [sighting :refer [NewSighting]]
             [identity-assertion :refer [NewIdentityAssertion]]
             [tool :refer [NewTool]]
             [vulnerability :refer [NewVulnerability]]
             [weakness :refer [NewWeakness]]]
            [flanders
             [spec :as fs]
             [utils :as fu]]))

(defn api-for-route [model-type entity-gen]
  (for-all
    [new-entity entity-gen]
    (let [app (helpers/get-current-app)
          get-in-config (helpers/current-get-in-config-fn app)
          {post-status :status
           {id :id
            type :type
            :as post-entity} :parsed-body}
          (post (str "ctia/" (name model-type))
                :body new-entity)]

      (if (not= 201 post-status)
        (throw (ex-info "POST did not return status 201"
                        post-entity)))

      (let [url-id
            (-> (id/->id type id (get-http-show get-in-config))
                :short-id
                encode)

            {get-status :status
             get-entity :parsed-body
             :as response}
            (get (str "ctia/" type "/" url-id))]

        (if (not= 200 get-status)
          (throw (ex-info "GET did not return status 200"
                          response)))

        (if-not (empty? (keys new-entity))
          (common= new-entity
                   post-entity
                   (dissoc get-entity :id))
          (common= post-entity
                   (dissoc get-entity :id)))))))

(doseq [[entity kw-ns]
        [[NewActor "max-new-actor"]
         [NewAsset "max-new-asset"]
         [NewAssetMapping "max-new-asset-mapping"]
         [NewAssetProperties "max-new-asset-properties"]
         [NewAttackPattern "max-new-attack-pattern"]
         [NewCampaign "max-new-campaign"]
         [NewCOA "max-new-coa"]
         [NewFeedback "max-new-feedback"]
         [NewIncident "max-new-incident"]
         [NewIndicator "max-new-indicator"]
         [NewJudgement "max-new-judgement"]
         [NewMalware "max-new-malware"]
         [NewRelationship "max-new-relationship"]
         [NewSighting "max-new-sighting"]
         [NewIdentityAssertion "max-new-identity-assertion"]
         [NewTool "max-new-tool"]
         [NewVulnerability "max-new-vulnerability"]
         [NewWeakness "max-new-weakness"]
         ;; TODO enable again once casebook/bundle/data_table
         ;;does not trigger StackOverFlow Exception
         ;;[NewCasebook "max-new-casebook"]
         ]]
  (fs/->spec (fu/require-all entity)
             kw-ns))

(defn spec-gen [kw-ns]
  (tcg/fmap #(dissoc % :id)
            (cs/gen (keyword kw-ns "map"))))

(def api-for-actor-routes
  (api-for-route 'actor
                 (spec-gen "max-new-actor")))

(def api-for-asset-routes
  (api-for-route 'asset (spec-gen "max-new-asset")))

(def api-for-asset-mapping-routes
  (api-for-route 'asset-mapping (spec-gen "max-new-asset-mapping")))

(def api-for-asset-properties-routes
  (api-for-route 'asset-properties (spec-gen "max-new-asset-properties")))

(def api-for-attack-pattern-routes
  (api-for-route 'attack-pattern
                 (spec-gen "max-new-attack-pattern")))

(def api-for-campaign-routes
  (api-for-route 'campaign
                 (spec-gen "max-new-campaign")))

(def api-for-coa-routes
  (api-for-route 'coa
                 (spec-gen "max-new-coa")))

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

(def api-for-malware-routes
  (api-for-route 'malware
                 (spec-gen "max-new-malware")))

(def api-for-relationship-routes
  (api-for-route 'relationship
                 (spec-gen "max-new-relationship")))

(def api-for-sighting-routes
  (api-for-route 'sighting
                 (spec-gen "max-new-sighting")))

(def api-for-identity-assertion-routes
  (api-for-route 'identity-assertion
                 (spec-gen "max-new-identity-assertion")))

(def api-for-tool-routes
  (api-for-route 'tool
                 (spec-gen "max-new-tool")))

(def api-for-vulnerability-routes
  (api-for-route 'vulnerability
                 (spec-gen "max-new-vulnerability")))

(def api-for-weakness-routes
  (api-for-route 'weakness
                 (spec-gen "max-new-weakness")))

;; TODO: uncomment that when we figure out why generative tests fail on data-table
#_(def api-for-casebook-routes
    (api-for-route 'casebook
                   (spec-gen "max-new-casebook")))
