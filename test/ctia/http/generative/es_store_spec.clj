(ns ctia.http.generative.es-store-spec
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.generative.specs :as specs]
            [clojure.test :refer [use-fixtures join-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia
                                    es-helpers/fixture-delete-store-indexes
                                    helpers/fixture-allow-all-auth]))

(defspec ^:generative spec-actor-routes-es-store
  specs/spec-actor-routes)

(defspec ^:generative spec-campaign-routes-es-store
  specs/spec-campaign-routes)

(defspec ^:generative spec-coa-routes-es-store
  specs/spec-coa-routes)

(defspec ^:generative spec-data-table-routes-es-store
  specs/spec-data-table-routes)

(defspec ^:generative spec-exploit-target-routes-es-store
  specs/spec-exploit-target-routes)

(defspec ^:generative spec-feedback-routes-es-store
  specs/spec-feedback-routes)

(defspec ^:generative spec-incident-routes-es-store
  specs/spec-incident-routes)

(defspec ^:generative spec-indicator-routes-es-store
  specs/spec-indicator-routes)

(defspec ^:generative spec-judgement-routes-es-store
  specs/spec-judgement-routes)

(defspec ^:generative spec-relationship-routes-es-store
  specs/spec-judgement-routes)

(defspec ^:generative spec-sighting-routes-es-store
  specs/spec-sighting-routes)

(defspec ^:generative spec-ttp-routes-es-store
  specs/spec-ttp-routes)
