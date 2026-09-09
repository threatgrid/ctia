(ns ctia.http.routes.swagger-json-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  helpers/fixture-ctia)

(deftest test-swagger-json
  (let [app (helpers/get-current-app)
        {:keys [status parsed-body]}
        (helpers/GET app "swagger.json" :accept :json)]
    (testing "We can get a swagger.json"
      (is (= 200 status)))
    (testing "the REST Verdict model description reflects XFV-20 org-scoping"
      ;; XFV-20 (B3): CTIM's Verdict entity description ("chosen from all of the
      ;; Judgements on that Observable") is overridden at the `def-acl-schema
      ;; Verdict` site so the REST/Swagger surface reads consistently with the
      ;; GraphQL one -- verdict calculation is tenant-local.
      (let [description (get-in parsed-body [:definitions :Verdict :description])]
        (is (string? description))
        (is (re-find #"caller's own org" description)
            "the swagger Verdict description must state the verdict is org-scoped")
        (is (not (re-find #"chosen from all" description))
            "the stale CTIM 'chosen from all ... Judgements' description must not survive")))))
