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

(def num-tests 40)

(defspec spec-actor-routes-atom-store num-tests
  specs/spec-actor-routes)

(defspec spec-campaign-routes-atom-store num-tests
  specs/spec-campaign-routes)

(defspec spec-coa-routes-atom-store num-tests
  specs/spec-coa-routes)

(defspec spec-exploit-target-routes-atom-store num-tests
  specs/spec-exploit-target-routes)

(defspec spec-feedback-routes-es-store num-tests
  specs/spec-feedback-routes)

(defspec spec-incident-routes-atom-store num-tests
  specs/spec-incident-routes)

(defspec spec-indicator-routes-atom-store num-tests
  specs/spec-indicator-routes)

(defspec spec-judgement-routes-atom-store num-tests
  specs/spec-judgement-routes)

(defspec spec-sighting-routes-atom-store num-tests
  specs/spec-ttp-routes)

(defspec spec-ttp-routes-atom-store num-tests
  specs/spec-ttp-routes)
