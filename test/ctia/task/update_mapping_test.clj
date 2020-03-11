(ns ctia.task.update-mapping-test
  (:require [clj-momo.lib.es.index :as es-index]
            [clojure.string :as string]
            [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
            [ctia.stores.es.init :as init]
            [ctia.task.update-mapping :as task]
            [ctia.test-helpers.core :as helpers :refer [post-bulk]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]))

(use-fixtures :once
  (join-fixtures [whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(use-fixtures :each
  (join-fixtures [helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(def examples (fixt/bundle 100 false))

(defn test-indices-mapping [idxs f]
  (testing "At least one index is present"
    (is (seq idxs)))
  (run! (fn [[_ {:keys [mappings] :as index}]]
          {:pre [(map? mappings)]}
          (f mappings))
        idxs))

(deftest update-mapping-test
  (let [; updates indices for `entity-kw` with a new field called `new-field-kw`
        ; with mapping `new-field-mapping`.
        entity-kw :incident
        new-field-kw :new-field
        new-field-mapping {:type "keyword", :include_in_all false, :normalizer "lowercase_normalizer"}
        ; the new mapping
        new-mappings {(name entity-kw)
                      {:properties
                       {(name new-field-kw) new-field-mapping}}}

        ; set up connection
        props {:entity entity-kw
               :indexname (str "ctia_" (name entity-kw))
               :host "localhost"
               :port 9200}
        state (init/init-es-conn! props)
        get-index #(es-index/get (:conn state) (:index state))

        ; ensure new field is absent
        _ (test-indices-mapping
            (get-index)
            (fn [mappings]
              (testing "Indices don't include new mapping"
                (is (nil? (get-in mappings [entity-kw :properties new-field-kw]))))))

        ; add field
        _ (task/update-mapping-store state new-mappings)

        ; ensure new field mapped correctly
        _ (test-indices-mapping
            (get-index)
            (fn [mappings]
              (testing "Indices include new mapping"
                (is (= (get-in mappings [entity-kw :properties new-field-kw])
                       new-field-mapping)))))]))
