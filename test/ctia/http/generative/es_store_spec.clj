(ns ctia.http.generative.es-store-spec
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.generative.properties :as prop]
            [clojure.test :refer [use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [ctia.test-helpers.core :as th]
            [ctia.test-helpers.es :as esh]))

(use-fixtures :once
  mth/fixture-schema-validation
  esh/fixture-properties:es-store
  th/fixture-allow-all-auth
  th/fixture-ctia
  esh/fixture-delete-store-indexes
  ;; The spec definitions below set all fields to be required
  ;; which we use to prove our ES mappings are complete
  th/fixture-spec-validation)

(defspec ^:generative api-for-actor-routes-es-store
  prop/api-for-actor-routes)

(defspec ^:generative api-for-asset-routes-es-store
  prop/api-for-asset-routes)

(defspec ^:generative api-for-asset-mapping-routes-es-store
  prop/api-for-asset-mapping-routes)

(defspec ^:generative api-for-asset-properties-routes-es-store
  prop/api-for-asset-properties-routes)
(defspec ^:generative api-for-target-record-routes-es-store
  prop/api-for-target-record-routes)

(defspec ^:generative api-for-attack-pattern-routes-es-store
  prop/api-for-attack-pattern-routes)

(defspec ^:generative api-for-campaign-routes-es-store
  prop/api-for-campaign-routes)

(defspec ^:generative api-for-coa-routes-es-store
  prop/api-for-coa-routes)

(defspec ^:generative api-for-feedback-routes-es-store
  prop/api-for-feedback-routes)

(defspec ^:generative api-for-incident-routes-es-store
  prop/api-for-incident-routes)

(defspec ^:generative api-for-indicator-routes-es-store
  prop/api-for-indicator-routes)

(defspec ^:generative api-for-judgement-routes-es-store
  prop/api-for-judgement-routes)

(defspec ^:generative api-for-malware-routes-es-store
  prop/api-for-malware-routes)

(defspec ^:generative api-for-relationship-routes-es-store
  prop/api-for-judgement-routes)

(defspec ^:generative api-for-sighting-routes-es-store
;  {:seed 1616133759541}
  prop/api-for-sighting-routes)

(defspec ^:generative api-for-identity-assertion-routes-es-store
  prop/api-for-identity-assertion-routes)

(defspec ^:generative api-for-tool-routes-es-store
  prop/api-for-tool-routes)

(defspec ^:generative api-for-vulnerability-routes-es-store
  prop/api-for-vulnerability-routes)

(defspec ^:generative api-for-weakness-routes-es-store
  prop/api-for-weakness-routes)

;; TODO this test is disabled for now as this entity contains
;; data-table which triggers a StackOverflow Exception, find a wat to enable it again
(defspec ^:generative api-for-casebook-routes-es-store
  ;; TODO this test consumes heap space at a high rate. 
  {:max-size 1
   :num-tests 1}
  prop/api-for-casebook-routes)
