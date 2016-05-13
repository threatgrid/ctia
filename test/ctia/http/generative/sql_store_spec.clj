(ns ctia.http.generative.sql-store-spec
  (:require [ctia.http.generative.specs :as specs]
            [clojure.test :refer [use-fixtures join-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.db :as db-helpers]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    db-helpers/fixture-properties:sql-store
                                    helpers/fixture-ctia
                                    db-helpers/fixture-db-recreate-tables
                                    helpers/fixture-allow-all-auth]))

(defspec spec-judgement-routes-sql-store
  specs/spec-judgement-routes)
