(ns ctia.http.handler.static-auth-anonymous-test
  (:require [ctia.test-helpers.core :as helpers :refer [GET with-properties]]
            [ctia.test-helpers.es :as es-helpers]
            [clojure.test :refer [deftest is use-fixtures]]
            [schema.test :refer [validate-schemas]]))

(defn fixture-anonymous-readonly-access
  [t]
  (with-properties
    ["ctia.auth.static.readonly-for-anonymous" true]
    (t)))

(use-fixtures :each
  validate-schemas
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
