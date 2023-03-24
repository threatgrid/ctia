(ns ctia.bulk.schemas-test
  (:require [ctia.bulk.schemas :as sut]
            [clojure.test :refer [deftest is testing]]
            [ctia.entity.incident :as incident]
            [ctia.features-service :as features-svc]
            [ctia.test-helpers.core :as helpers]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema FakeEntitySchema
  {:title s/Str
   :source s/Str})

(def PartialFakeEntitySchema
  (st/optional-keys-schema FakeEntitySchema))

(deftest entity-schema-test
  (testing "ensure that incident scores are properly configured in NewBulk schema"
    (with-app-with-config app
      [features-svc/features-service]
      {:ctia {:http {:incident {:score-types "global,ttp,asset"}}}}
      (let [services (helpers/app->GetEntitiesServices app)
            fake-entity {:new-schema (fn [services] FakeEntitySchema)
                         :partial-schema PartialFakeEntitySchema
                         :entity :fake-entity
                         :plural :fake-entities}]
        (doseq [[sch-op expected] [[:new-schema FakeEntitySchema]
                                   [:partial-schema PartialFakeEntitySchema]]]
          (is (= {:fake_entities [(s/maybe expected)]}
                 (sut/entity-schema fake-entity sch-op services))))
          (is (= {:fake_entities s/Int}
                 (sut/entity-schema fake-entity s/Int services)))))))
