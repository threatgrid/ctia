(ns ctia.http.handler.static-auth-anonymous-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [post get with-properties]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctim.domain.id :as id]))

(defn fixture-anonymous-readonly-access
  [t]
  (with-properties
    ["ctia.auth.static.readonly-for-anonymous" true]
    (t)))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  helpers/fixture-properties:clean
  es-helpers/fixture-properties:es-store
  (helpers/fixture-properties:static-auth "kitara" "tearbending")
  fixture-anonymous-readonly-access
  helpers/fixture-ctia)

(deftest anonymous-readonly-access-test
  (let [{status :status}
        (get (str "ctia/judgement/search")
             :query-params {:query "*"}
             :headers {"Authorization" "bloodbending"})]
    (is (= 200 status)))

  (let [{status :status}
        (get (str "ctia/judgement/search")
             :query-params {:query "*"})]
    (is (= 200 status))))
