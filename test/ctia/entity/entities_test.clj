(ns ctia.entity.entities-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.entity.entities :as sut]
            [ctia.schemas.core :refer [lift-realize-fn-with-context]]
            [ctia.test-helpers
             [core :as test-helpers]
             [es :as es-helpers]]
            [ctia.lib.utils :refer [service-subgraph]]
            [clojure.test :as t :refer [deftest is use-fixtures]]
            [clojure.spec.alpha :refer [gen]]
            [clojure.spec.gen.alpha :refer [generate]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

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
        realize-fn-services (service-subgraph
                              (app/service-graph app)
                              :ConfigService [:get-in-config]
                              :StoreService [:read-store]
                              :GraphQLNamedTypeRegistryService
                              [:get-or-update-named-type-registry]
                              :IEncryption [:decrypt :encrypt])
        properties [:id :type :owner :groups :schema_version
                    :created :modified :timestamp :tlp]
        ;; properties to dissoc to get a valid entity when
        ;; using the spec generator
        to-dissoc [:disposition]
        entities-with-realize-fn (filter (comp :realize-fn val) sut/entities)]
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

