(ns ctia.entity.entities-test
  (:require [ctia.entity.entities :as sut]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.http.server-service :as server-svc]
            [clojure.test :as t :refer [deftest is use-fixtures join-fixtures]]
            [clojure.spec.alpha :refer [gen]]
            [clojure.spec.gen.alpha :refer [generate]]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-ctia-fast]))

(defn gen-sample-entity
  [{:keys [new-spec]}]
  (if new-spec
    (generate (gen new-spec))
    {}))

(defn resolve-maybe-delayed-entity [maybe-entity server-svc]
  (if (fn? maybe-entity)
    (maybe-entity {:services (server-svc/get-ctia-http-server-service-dependencies server-svc)})
    maybe-entity))

(deftest entity-realize-fn-test
  (let [app (test-helpers/get-current-app)
        server-svc (app/get-service app :CTIAHTTPServerService)
        properties [:id :type :owner :groups :schema_version
                    :created :modified :timestamp :tlp]
        ;; properties to dissoc to get a valid entity when
        ;; using the spec generator
        to-dissoc [:disposition]]
    (doseq [[_ {:keys [realize-fn] :as entity}] sut/entities]
      (when realize-fn
        (let [realized-entity
              (-> (apply dissoc
                         (gen-sample-entity entity)
                         (concat properties to-dissoc))
                  (realize-fn "http://host/id" {} "owner" [])
                  (resolve-maybe-delayed-entity server-svc))]
          (doseq [property properties]
            (is (contains? realized-entity property)
                (format "The realized entity %s should contain the property %s"
                        (:entity entity)
                        property))))))))

