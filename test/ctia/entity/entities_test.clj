(ns ctia.entity.entities-test
  (:require [ctia.entity.entities :as sut]
            [ctia.schemas.core :refer [lift-realize-fn-with-context RealizeFnServices]]
            [ctia.schemas.utils :as csu]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.test-helpers.es :as es-helpers]
            [clojure.test :as t :refer [deftest is use-fixtures]]
            [clojure.spec.alpha :refer [gen]]
            [clojure.spec.gen.alpha :refer [generate]]
            [puppetlabs.trapperkeeper.app :as app]))

;; FIXME this seems to fail because the specs used to generate
;; data via gen-sample-entity don't agree with the schemas
#_
(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  es-helpers/fixture-properties:es-store
  test-helpers/fixture-ctia-fast)

(defn gen-sample-entity
  [{:keys [new-spec]}]
  (if new-spec
    (generate (gen new-spec))
    {}))

(deftest entity-realize-fn-test
  (let [app (test-helpers/get-current-app)
        realize-fn-services (csu/select-service-subgraph
                              (app/service-graph app)
                              RealizeFnServices)
        properties [:id :type :owner :groups :schema_version
                    :created :modified :timestamp :tlp]
        ;; properties to dissoc to get a valid entity when
        ;; using the spec generator
        to-dissoc [:disposition]
        entities-with-realize-fn (filter (comp :realize-fn val) (sut/all-entities))]
    (assert (seq entities-with-realize-fn)
            "There should really be a :realize-fn somewhere!")
    (doseq [[_ entity] entities-with-realize-fn
            :let [realize-fn (-> (:realize-fn entity)
                                 (lift-realize-fn-with-context
                                   {:services realize-fn-services}))
                  realized-entity
                  (-> (apply dissoc
                             (gen-sample-entity entity)
                             (concat properties to-dissoc))
                      (realize-fn "http://host/id" {} "owner" []))]
            property properties]
      (is (contains? realized-entity property)
          (format "The realized entity %s should contain the property %s"
                  (:entity entity)
                  property)))))

