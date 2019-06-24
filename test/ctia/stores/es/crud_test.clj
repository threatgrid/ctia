(ns ctia.stores.es.crud-test
  (:require [clojure.test :as t :refer [is testing deftest use-fixtures join-fixtures]]
            [schema.core :as s]
            [clj-momo.lib.es.index :as es-index]
            [ctia.stores.es.crud :as sut]
            [ctia.stores.es.init :as init]

            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]))

(deftest partial-results-test
  (is (= {:data [{:error "Exception"
                  :id "123"}
                 {:id "124"}]}
         (sut/partial-results
          {:es-http-res-body
           {:took 3
            :errors true
            :items [{:index
                     {:_index "website"
                      :_type "blog"
                      :_id "123"
                      :status 400
                      :error "Exception"}}
                    {:index
                     {:_index "website"
                      :_type "blog"
                      :_id "124"
                      :_version 5
                      :status 200}}]}}
          [{:_id "123"
            :id "123"}
           {:_id "124"
            :id "124"}]
          identity))))

(use-fixtures :each
  (join-fixtures [es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(deftest rollover-crud-test
  (let [ident {:groups ["group1"]}
        props-aliased {:entity :sighting
                       :indexname "ctia_sighting"
                       :host "localhost"
                       :port 9200
                       :aliased true
                       :rollover {:max_docs 3}
                       :refresh "true"}
        state-aliased (init/init-es-conn! props-aliased)
        rollover-aliased (sut/rollover state-aliased)

        props-not-aliased {:entity :sighting
                           :indexname "ctia_sighting"
                           :host "localhost"
                           :port 9200}
        state-not-aliased (update state-aliased :props dissoc :aliased)
        rollover-not-aliased (sut/rollover state-not-aliased)

        create-fn (sut/handle-create :sighting s/Any)
        update-fn (sut/handle-update :sighting s/Any)
        read-fn (sut/handle-read :sighting s/Any)
        delete-fn (sut/handle-delete :sighting s/Any)
        count-index #(count (es-index/get (:conn state-aliased)
                                          (str (:index state-aliased) "*")))
        base-sighting {:title "a sighting"
                       :tlp "green"
                       :groups ["group1"]}]
    (is (nil? rollover-not-aliased))
    (is (seq rollover-aliased))
    (is (false? (:rolled_over rollover-aliased)))

    (create-fn state-not-aliased
               (map #(assoc base-sighting
                            :id (str "sighting-" %))
                    (range 3))
              ident
               {})
    (is (= 1 (count-index)) ;; total docs: 3
        "rollover should not be triggered when :aliased property is set to false")

    (create-fn state-aliased
               (repeat 1 {:title "a sighting"})
               {}
               {})
    ;; total docs: 4
    (is (= 2 (count-index))
        "exceeding max_doc condition should trigger rollover when :aliased is set to false")

    (create-fn state-aliased
               (repeat 1 {:title "a sighting"})
               {}
               {})
    (is (= 2 (count-index)) ;; total docs in last index: 1
        "not exceeding max_doc condition in current index, should not trigger rollover")

    (create-fn state-aliased
               (repeat 3 {:title "a sighting"})
               {}
               {})
    (is (= 3 (count-index)) ;; total docs in last index: 3
        "when index size is equal to max_doc condition it should trigger rollover when :aliased is set to false")

    (is (= '"sighting-1"
           (:id (read-fn state-aliased "sighting-1" ident {}))
           (:id (read-fn state-not-aliased "sighting-1" ident {})))
        "handle read should properly retrieve inserted documents with aliased and not aliased conf")

    (is (= "value1"
           (:updated-field (update-fn state-aliased
                                      "sighting-1"
                                      (assoc base-sighting
                                             :updated-field
                                             "value1")
                                      ident)))
        "handle update should properly update documents with aliases")
    (is (= "value2"
           (:updated-field (update-fn state-not-aliased
                                      "sighting-1"
                                      (assoc base-sighting
                                             :updated-field
                                             "value2")
                                      ident)))
        "handle update should properly update documents even with aliased property modified to false")

    (is (true? (delete-fn state-aliased
                          "sighting-1"
                          ident))
        "handle-delete should properly delete documents with aliases")

    (is (true? (delete-fn state-not-aliased
                          "sighting-2"
                          ident))
        "handle delete should properly delete documents even with aliased property modified to false")))
