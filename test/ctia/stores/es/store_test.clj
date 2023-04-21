(ns ctia.stores.es.store-test
  (:require [ctia.store :as store]
            [ctia.stores.es.store :as sut]
            [ctia.entity.entities :as entities]
            [ctia.entity.sighting :as sighting]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.core :as helpers]
            [ctim.examples.incidents :refer [incident-minimal]]
            [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def admin-ident {:login "johndoe"
                  :groups ["Administators"]})

(deftest all-pages-iteration-test
  (testing "all pages shall properly list all results for given entity and parameters."
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [{{:keys [get-store]} :StoreService} (app->APIHandlerServices app)
             incidents-1 (fixt/n-doc incident-minimal 10000)
             incidents-2 (->> (fixt/n-doc incident-minimal 10000)
                              (map #(assoc % :source "incident-2-source")))
             incidents (concat incidents-1 incidents-2)
             _ (helpers/POST-bulk app {:incidents incidents})
             incident-store (get-store :incident)]

         (letfn [(make-query-fn [store filters identity-map]
                   (fn query-fn [query-params]
                     (store/list-records store
                                         filters
                                         identity-map
                                         query-params)))]

           (let [query-incidents (make-query-fn
                                  incident-store
                                  {:query "*"}
                                  admin-ident)]

             (testing "all-pages-iteration is lazy"
               (let [calls-counter (atom 0)
                     iter (sut/all-pages-iteration
                           (fn [params]
                             (swap! calls-counter inc)
                             (query-incidents params))
                           {})
                     _ (first iter)]
                 (is (= 1 @calls-counter))
                 (let [_ (rest iter)]
                   (is (= 2 @calls-counter)))))

             (testing "default limit is set"
               (let [{:keys [data]
                      {{:keys [limit]} :next} :paging}
                     (first (sut/all-pages-iteration query-incidents {}))]
                 (is (= limit 100))
                 (is (= (count data) 100))))

             (testing "limit does not affect total number of retrieved entities"
               (let [iter (sut/all-pages-iteration query-incidents {})
                     iter1000 (sut/all-pages-iteration query-incidents {:limit 1000})]
                 (is (= (count incidents)
                        (count (sequence
                                (mapcat :data)
                                iter))
                        (count (sequence
                                (mapcat :data)
                                iter1000)))))))

           (testing "entities should be properly filtered"
             (let [query-incidents (make-query-fn
                                    incident-store
                                    {:one-of {:source "incident-2-source"}}
                                    admin-ident)]

               (is (= (count incidents-2)
                      (count (sequence
                              (mapcat :data)
                              (sut/all-pages-iteration query-incidents {:limit 10000})))))))))))))

;(s/defschema Stored {:stored1 [s/Any] :stored2 [s/Any]})
;(s/defschema ESStored (st/assoc Stored :es-stored [s/Any]))
;(s/defschema PartialStored (st/optional-keys Stored))
;(s/defschema ESPartialStored (st/optional-keys ESStored))
;
;(def ^:dynamic *stored->es-stored* (comp  :doc))
;(def ^:dynamic *es-stored->stored* (comp #(-> % (dissoc :es-stored) (update :stored (fnil conj []) [:es-stored->stored %])) :doc))
;(def ^:dynamic *es-partial-stored->partial-stored* (comp #(-> % (dissoc :es-stored) (update :stored (fnil conj []) [:es-stored->stored %])) :doc))
;
;(sut/def-es-store SightingStore :sighting StoredSighting PartialStoredSighting
;  :store-opts {:stored->es-stored #'*stored->es-stored*
;               :es-stored->stored #'*es-stored->stored*
;               :es-partial-stored->partial-stored #'*es-partial-stored->partial-stored*
;               :es-stored-schema ESStored
;               :es-partial-stored-schema ESPartialStored})
;
;(deftest def-es-store-test
;  (testing ":state"
;    (let [g (gensym)]
;      (is (= g (-> g ->SightingStore :state)))))
;  (with-redefs [;; TODO parameterize app by entities map
;                entities/all-entities (let [orig entities/all-entities]
;                                        (fn []
;                                          (-> (orig)
;                                              (assoc :sighting
;                                                     (assoc sighting/sighting-entity
;                                                            :es-store ->SightingStore)))))]
;    (helpers/fixture-ctia-with-app
;      (fn [app]
;        (testing "read-record"
;          (let [{{:keys [get-store]} :StoreService} (app->APIHandlerServices app)
;                res (store/read-record (->DefEsStoreTest {:service {:ConfigService {:get-in-config }}}))]
;            ))))))
