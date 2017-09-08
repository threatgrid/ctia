(ns ctia.http.generative.es-store-spec
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.generative.properties :as prop]
            [clojure.test :refer [use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [ctia.test-helpers.core :as th]
            [ctia.test-helpers.es :as esh]))

(use-fixtures :once
  mth/fixture-schema-validation
  th/fixture-properties:clean
  esh/fixture-properties:es-store
  th/fixture-ctia
  esh/fixture-delete-store-indexes
  th/fixture-allow-all-auth
  ;; The spec definitions below set all fields to be required
  ;; which we use to prove our ES mappings are complete
  th/fixture-spec-validation
  th/fixture-fast-gen)

(defspec ^:generative api-for-actor-routes-es-store
  prop/api-for-actor-routes)

(defspec ^:generative api-for-campaign-routes-es-store
  prop/api-for-campaign-routes)

(defspec ^:generative api-for-coa-routes-es-store
  prop/api-for-coa-routes)

(defspec ^:generative api-for-exploit-target-routes-es-store
  prop/api-for-exploit-target-routes)

(defspec ^:generative api-for-feedback-routes-es-store
  prop/api-for-feedback-routes)

(defspec ^:generative api-for-incident-routes-es-store
  prop/api-for-incident-routes)

(defspec ^:generative api-for-indicator-routes-es-store
  prop/api-for-indicator-routes)

(defspec ^:generative api-for-judgement-routes-es-store
  prop/api-for-judgement-routes)

(defspec ^:generative api-for-relationship-routes-es-store
  prop/api-for-judgement-routes)

(defspec ^:generative api-for-sighting-routes-es-store
  prop/api-for-sighting-routes)

(defspec ^:generative api-for-ttp-routes-es-store
  prop/api-for-ttp-routes)
