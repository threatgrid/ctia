(ns ctia.graphql.schemas-test
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest are is testing use-fixtures]]
   [ctia.graphql.schemas :as schemas]
   [ctia.test-helpers.core :as th]
   [ctia.test-helpers.es :as es-helpers]
   [puppetlabs.trapperkeeper.app :as tk-app]))

(use-fixtures :each es-helpers/fixture-properties:es-store)

(deftest graphql-schemas-test
  (testing "auxiliary functions"
    (th/with-properties ["ctia.features.disable" "asset,actor,sighting,asset-mapping"]
      (th/fixture-ctia-with-app
       (fn [app]
         (let [services (tk-app/service-graph app)]
           (testing "disabled entities are recognized"
             (is (= #{:asset :actor :sighting :asset-mapping}
                    (set (#'schemas/disabled-entities services)))))
           (testing "graphql keys for disabled entities get removed"
             (let [graphql-keys (->>
                                 schemas/graphql-fields
                                 (#'schemas/remove-disabled services)
                                 keys
                                 set)]
               (is (false? (set/subset?
                            #{:asset         :assets
                              :actor         :actors
                              :sighting      :sightings
                              :asset_mapping :asset_mappings}
                            graphql-keys)))
               (is (set/subset?
                    #{:asset_properties :indicator :indicators}
                    graphql-keys))))))))))
