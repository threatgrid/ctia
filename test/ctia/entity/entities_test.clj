(ns ctia.entity.entities-test
  (:require [ctia.entity.entities :as sut]
            [ctia.schemas.core :refer [lift-realize-fn-with-context]]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.http.server :refer [realize-fn-global-services]]
            [clojure.test :as t :refer [deftest is use-fixtures join-fixtures]]
            [clojure.spec.alpha :refer [gen]]
            [clojure.spec.gen.alpha :refer [generate]]))

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-ctia-fast]))

(defn gen-sample-entity
  [{:keys [new-spec]}]
  (if new-spec
    (generate (gen new-spec))
    {}))

(deftest entity-realize-fn-test
  (let [realize-fn-services (realize-fn-global-services)
        properties [:id :type :owner :groups :schema_version
                    :created :modified :timestamp :tlp]
        ;; properties to dissoc to get a valid entity when
        ;; using the spec generator
        to-dissoc [:disposition]
        entities-with-realize-fn (filter :realize-fn sut/entities)]
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

