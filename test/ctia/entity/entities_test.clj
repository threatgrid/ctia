(ns ctia.entity.entities-test
  (:require [ctia.entity.entities :as sut]
            [ctia.schemas.core :refer [MaybeDelayedRealizeFn->RealizeFn]]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.http.server-service :as server-svc]
            [clojure.test :as t :refer [deftest is use-fixtures join-fixtures]]
            [clojure.spec.alpha :refer [gen]]
            [clojure.spec.gen.alpha :refer [generate]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-ctia-fast]))

(defn gen-sample-entity
  [{:keys [new-spec]}]
  (if new-spec
    (generate (gen new-spec))
    {}))

(deftest entity-realize-fn-test
  (let [app (test-helpers/get-current-app)
        realize-fn-services (-> (app/get-service app :CTIAHTTPServerService)
                                service-context
                                :services)
        properties [:id :type :owner :groups :schema_version
                    :created :modified :timestamp :tlp]
        ;; properties to dissoc to get a valid entity when
        ;; using the spec generator
        to-dissoc [:disposition]]
    (doseq [[_ {:keys [realize-fn] :as entity}] sut/entities]
      (when-some [realize-fn (some-> realize-fn 
                                     (MaybeDelayedRealizeFn->RealizeFn
                                       {:services realize-fn-services}))]
        (let [realized-entity
              (-> (apply dissoc
                         (gen-sample-entity entity)
                         (concat properties to-dissoc))
                  (realize-fn "http://host/id" {} "owner" []))]
          (doseq [property properties]
            (is (contains? realized-entity property)
                (format "The realized entity %s should contain the property %s"
                        (:entity entity)
                        property))))))))

