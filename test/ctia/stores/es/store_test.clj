(ns ctia.stores.es.store-test
  (:require [clojure.set :as set]
            [ctia.store :as store]
            [ctia.stores.es.store :as sut]
            [ctia.entity.entities :as entities]
            [ctia.entity.sighting :as sighting]
            [ctim.examples.sightings :refer [sighting-minimal]]
            [ctia.entity.sighting.schemas :refer [PartialStoredSighting StoredSighting]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.http :refer [app->APIHandlerServices]]
            [ctia.test-helpers.fixtures :as fixt]
            [ctia.test-helpers.core :as helpers]
            [ctim.examples.incidents :refer [incident-minimal]]
            [clojure.test :refer [deftest testing is]]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]
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

(sut/def-es-store SightingStore :sighting StoredSighting PartialStoredSighting 
  :store-opts {:stored->es-stored (fn [{:keys [doc]}] (update doc :title str " stored->es-stored"))
               ;;TODO unit test. only used for the error case of `handle-create`
               :es-stored->stored (fn [{:keys [doc]}] (update doc :title str " es-stored->stored"))
               :es-partial-stored->partial-stored (fn [{:keys [doc]}] (update doc :title str " es-partial-stored->partial-stored"))
               :es-stored-schema StoredSighting
               :es-partial-stored-schema PartialStoredSighting})

(def ident {:login "foouser"
            :groups ["foogroup"]})
(def base-sighting (-> sighting-minimal
                       (into (set/rename-keys ident {:login :owner}))
                       (assoc :created (java.util.Date.)
                              :title "a sighting text title")))

(deftest def-es-store-test
  ;; FIXME move to use-fixtures :once (all-pages-iteration-test fails with schemas enabled)
  (validate-schemas
    (fn []
      (testing ":state"
        (let [g (gensym)]
          (is (= g (-> g ->SightingStore :state)))))
      (testing ":store-opts transformers"
        (helpers/with-properties (-> ["ctia.auth.type" "allow-all"]
                                     (into es-helpers/basic-auth-properties))
          (helpers/fixture-ctia-with-app
            (fn [app]
              (let [{{:keys [get-store]} :StoreService} (app->APIHandlerServices app)
                    store (->SightingStore (:state (get-store :sighting)))
                    params {:refresh "wait_for"}
                    id "sighting1"
                    is-title #(do (is (= % (:title (store/read-record store id ident params))))
                                  (is (= [%] (->> (store/read-records store [id] ident params)
                                                  (mapv :title))))
                                  (is (= [%] (->> (store/list-records store {} ident params)
                                                  :data (mapv :title))))
                                  (is (= [%] (->> (store/query-string-search store {:ident ident :params params :search-query {:filter-map {:id id}}})
                                                  :data (mapv :title)))))]
                (testing "create-record"
                  (let [base-sighting (assoc base-sighting :id id :title "create-record")]
                    (is (= "create-record"
                           (-> (store/create-record store [base-sighting] ident params)
                               first
                               :title)))
                    (is-title "create-record stored->es-stored es-partial-stored->partial-stored")))
                (testing "update-record"
                  (let [base-sighting (assoc base-sighting :title "update-record")]
                    (is (= "update-record"
                           (:title (store/update-record store id base-sighting ident params))))
                    (is-title "update-record stored->es-stored es-partial-stored->partial-stored")))
                (testing "bulk-update"
                  (let [base-sighting (assoc base-sighting :id id :title "bulk-update")]
                    (is (= {:updated [id]}
                           (store/bulk-update store [base-sighting] ident params)))
                    (is-title "bulk-update stored->es-stored es-partial-stored->partial-stored")))))))))))
