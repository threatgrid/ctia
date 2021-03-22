(ns ctia.http.generative.es-store-spec
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.generative.properties :as prop]
            [clojure.test :refer [deftest use-fixtures]]
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

(deftest ^:generative api-for-actor-routes-es-store
  (prop/api-for-actor-routes
    100))

(deftest ^:generative api-for-asset-routes-es-store
  (prop/api-for-asset-routes
    100))

(deftest ^:generative api-for-asset-mapping-routes-es-store
  (prop/api-for-asset-mapping-routes
    100))

(deftest ^:generative api-for-asset-properties-routes-es-store
  (prop/api-for-asset-properties-routes
    100))
(deftest ^:generative api-for-target-record-routes-es-store
  (prop/api-for-target-record-routes
    100))

(deftest ^:generative api-for-attack-pattern-routes-es-store
  (prop/api-for-attack-pattern-routes
    100))

(deftest ^:generative api-for-campaign-routes-es-store
  (prop/api-for-campaign-routes
    100))

(deftest ^:generative api-for-coa-routes-es-store
  (prop/api-for-coa-routes
    100))

(deftest ^:generative api-for-feedback-routes-es-store
  (prop/api-for-feedback-routes
    100))

(deftest ^:generative api-for-incident-routes-es-store
  (prop/api-for-incident-routes
    100))

(deftest ^:generative api-for-indicator-routes-es-store
  (prop/api-for-indicator-routes
    100))

(deftest ^:generative api-for-judgement-routes-es-store
  (prop/api-for-judgement-routes
    100))

(deftest ^:generative api-for-malware-routes-es-store
  (prop/api-for-malware-routes
    100))

(deftest ^:generative api-for-relationship-routes-es-store
  (prop/api-for-judgement-routes
    100))

(deftest ^:generative api-for-sighting-routes-es-store
  (prop/api-for-sighting-routes
    100))

(deftest ^:generative api-for-identity-assertion-routes-es-store
  (prop/api-for-identity-assertion-routes
    100))

(deftest ^:generative api-for-tool-routes-es-store
  (prop/api-for-tool-routes
    100))

(deftest ^:generative api-for-vulnerability-routes-es-store
  (prop/api-for-vulnerability-routes
    100))

(deftest ^:generative api-for-weakness-routes-es-store
  (prop/api-for-weakness-routes
    100))

(deftest ^:disabled api-for-casebook-routes-es-store
  ;; TODO identify why this is slow
  (prop/api-for-casebook-routes
    {:max-size 1
     :num-tests 1}))
