(ns ctia.bulk.core-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.set :as set]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [ctia.auth.allow-all :refer [identity-singleton]]
            [ctia.bulk.core :as sut :refer [read-fn]]
            [ctia.bulk.schemas :refer [NewBulk]]
            [ctia.features-service :as features-svc]
            [ctia.test-helpers.core :as helpers]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [get-in-config (helpers/build-get-in-config-fn)
          res (sut/read-entities ["judgement-123"]
                                 :judgement
                                 identity-singleton
                                 {:ConfigService {:get-in-config get-in-config}
                                  :StoreService {:get-store (constantly nil)}})]
      (is (= [nil] res)))))

(defn schema->keys
  "Extracts both: required and optional keys of schema as set of keywords"
  [schema]
  (let [reqs (->> schema st/required-keys keys)
        opts (->> schema st/optional-keys keys (map :k))]
    (set (concat reqs opts))))

(deftest bulk-schema-excludes-disabled-test
  (testing "ensure NewBulk schema includes only enabled entities"
    (with-app-with-config app
      [features-svc/features-service] {:ctia {:features {}}}
      (let [bulk-schema (NewBulk (helpers/app->ConfigurationServices app))]
        (schema->keys bulk-schema)
        (is (true? (set/subset? #{:assets :actors} (schema->keys bulk-schema))))))
    (with-app-with-config app
      [features-svc/features-service]
      {:ctia {:features {:disable "asset,actor"}}}
      (let [bulk-schema (NewBulk (helpers/app->ConfigurationServices app))]
        (is (false? (set/subset? #{:assets :actors} (schema->keys bulk-schema))))))))
