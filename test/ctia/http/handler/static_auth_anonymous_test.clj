(ns ctia.http.handler.static-auth-anonymous-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [GET with-properties]]
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
  es-helpers/fixture-properties:es-store
  (helpers/fixture-properties:static-auth "kitara" "tearbending")
  fixture-anonymous-readonly-access
  helpers/fixture-ctia)

(deftest anonymous-readonly-access-test
  (let [app (helpers/get-current-app)
        {status :status}
        (GET app
             (str "ctia/judgement/search")
             :query-params {:query "*"}
             :headers {"Authorization" "bloodbending"})
        _ (is (= 200 status))

        {status :status}
        (GET app
             (str "ctia/judgement/search")
             :query-params {:query "*"})]
    (is (= 200 status))))
