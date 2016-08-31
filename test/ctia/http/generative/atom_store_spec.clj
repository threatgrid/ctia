(ns ctia.http.generative.atom-store-spec
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.http.generative.specs :as specs]
            [clojure.test :refer [use-fixtures join-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [ctia.test-helpers
             [atom :as at-helpers]
             [core :as helpers]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    at-helpers/fixture-properties:atom-memory-store
                                    helpers/fixture-ctia
                                    helpers/fixture-allow-all-auth]))

(defspec spec-actor-routes-atom-store
  specs/spec-actor-routes)

(defspec spec-campaign-routes-atom-store
  specs/spec-campaign-routes)

(defspec spec-coa-routes-atom-store
  specs/spec-coa-routes)

(defspec spec-exploit-target-routes-atom-store
  specs/spec-exploit-target-routes)

(defspec spec-feedback-routes-es-store
  specs/spec-feedback-routes)

(defspec spec-incident-routes-atom-store
  specs/spec-incident-routes)

(defspec spec-indicator-routes-atom-store
  specs/spec-indicator-routes)

(defspec spec-judgement-routes-atom-store
  specs/spec-judgement-routes)

(defspec spec-sighting-routes-atom-store
  specs/spec-sighting-routes)

(defspec spec-ttp-routes-atom-store
  specs/spec-ttp-routes)

(defspec spec-bundle-routes-atom-store
  specs/spec-bundle-routes)
