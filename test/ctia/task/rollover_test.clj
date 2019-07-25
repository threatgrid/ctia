(ns ctia.task.rollover-test
  (:require [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [clj-momo.lib.es
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]

            [ctia.stores.es.init :as init]
            [ctia.properties :as props]
            [ctia.test-helpers
             [fixtures :as fixt]
             [core :as helpers :refer [post-bulk delete]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.task.rollover :as sut]))

(use-fixtures :once
  (join-fixtures [whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(use-fixtures :each
  (join-fixtures [helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(def examples (fixt/bundle 100 false))

(deftest rollover-aliased-test
  (let [props-not-aliased {:entity :malware
                           :indexname "ctia_malware"
                           :host "localhost"
                           :port 9200}
        state-not-aliased (init/init-es-conn! props-not-aliased)
        rollover-not-aliased (sut/rollover-store state-not-aliased)
        props-aliased {:entity :sighting
                       :indexname "ctia_sighting"
                       :host "localhost"
                       :port 9200
                       :aliased true
                       :rollover {:max_docs 3}
                       :refresh "true"}
        state-aliased (init/init-es-conn! props-aliased)
        rollover-aliased (sut/rollover-store state-aliased)

        count-index #(count (es-index/get (:conn state-aliased)
                                          (str (:index state-aliased) "*")))]
    (is (nil? rollover-not-aliased))
    (is (seq rollover-aliased))
    (is (false? (:rolled_over rollover-aliased)))
    (is (= 1 (count-index)))
    (post-bulk examples)
    (es-index/refresh! (:conn state-aliased))
    (is (nil? (sut/rollover-store state-not-aliased)))
    (is (= 1 (count-index)))
    (is (true? (:rolled_over (sut/rollover-store state-aliased))))
    (is (= 2 (count-index)))
    (post-bulk examples)
    (es-index/refresh! (:conn state-aliased))
    (is (true? (:rolled_over (sut/rollover-store state-aliased))))
    (is (= 3 (count-index)))))
