(ns ctia.flows.crud-test
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.test :refer [deftest is testing]]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [ctia.entity.sighting.schemas :as ss]
            [ctia.flows.crud :as flows.crud]
            [ctia.lib.collection :as coll]
            [ctia.store :refer [query-string-search]]
            [ctia.test-helpers.core :as helpers]
            [ctim.examples.sightings :refer [sighting-minimal]]
            [ctia.domain.entities :refer [short-id->long-id]]
            [java-time.api :as jt]
            [puppetlabs.trapperkeeper.app :as app]))

(deftest deep-merge-with-add-colls-test
  (let [fixture {:foo {:bar ["one" "two" "three"]
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar ["one" "two" "three" "four"]
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/add-colls
                            fixture
                            {:foo {:bar ["four"]}})))))

(deftest deep-merge-with-remove-colls-test
  (let [fixture {:foo {:bar #{"one" "two" "three"}
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar #{"one" "three"}
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/remove-colls
                            fixture
                            {:foo {:bar ["two"]}})))))

(deftest deep-merge-with-replace-colls-test
  (let [fixture {:foo {:bar {:foo {:bar ["something" "or" "something" "else"]}}
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar {:foo {:bar ["else"]}}
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/replace-colls
                            fixture
                            {:foo  {:bar {:foo {:bar #{"else"}}}}})))))

(deftest preserve-errors-test
  (testing "with enveloped result"
    (let [f (fn [_]
              {:entities
               [{:id "4"}
                {:id "2"}]
               :enveloped-result? true})
          entities [{:id "1"}
                    {:id "2"}
                    {:id "3"
                     :error "msg"}
                    {:id "4"}]]
      (is (= {:entities
              [{:id "2"}
               {:id "3"
                :error "msg"}
               {:id "4"}]
              :enveloped-result? true}
             (flows.crud/preserve-errors {:entities entities
                                          :enveloped-result? true}
                                         f)))))
  (testing "without enveloped result"
    (is (= {:entities
            [{:id "1"
              :title "title"}]}
           (flows.crud/preserve-errors
            {:entities [{:id "1"}]}
            (fn [_]
              {:entities
               [{:id "1"
                 :title "title"}]}))))))

(deftest apply-create-store-fn-test
  (let [store-fn-create (partial map #(assoc % :applied-store-create true))
        flow-base {:entity-type :indicator
                   :identity :whatever
                   :create-event-fn identity
                   :flow-type :create
                   :store-fn store-fn-create}
        flow-empty-entities (assoc flow-base :entities '())
        flow-with-entities (assoc flow-base
                                  :entities '({:type :fake
                                               :id 1}
                                              {:type :fake
                                               :id 2}))]
    (is (= flow-empty-entities
           (flows.crud/apply-create-store-fn flow-empty-entities))
        "when entities are empty, apply-create-store-fn should not apply store-fn")
    (is (every? #(:applied-store-create %)
                (:entities (flows.crud/apply-create-store-fn flow-with-entities)))
        "store-fn shall be applied to every entities")))

(deftest create-events-test
  (testing "create-events shall filter errored entities and return passed flow with corresponding events owned by current user"
    (let [login "test-user"
          ident (map->Identity {:login login})
          fake-event (fn [entity]
                       {:owner login
                        :entity entity})
          to-create-event (fn [entity _ _]
                            (fake-event entity))
          to-update-event (fn [entity _ _ _]
                            (fake-event entity))
          to-delete-event (fn [entity _ _]
                            (fake-event entity))
          valid-entities [{:id 1 :owner "Huey"}
                          {:id 2 :owner "Dewey"}
                          {:id 3 :owner "Louie"}]
          get-prev-entity (fn [id]
                            (first
                             (filter #(= id (:id %))
                                     valid-entities)))
          entities-with-error (conj valid-entities {:error "something bad happened"})
          base-flow-map {:services {:ConfigService {:get-in-config (constantly true)}}
                         :identity ident
                         :entities entities-with-error}
          create-flow-map (assoc base-flow-map
                                 :flow-type :create
                                 :create-event-fn to-create-event)
          update-flow-map (assoc base-flow-map
                                 :flow-type :update
                                 :get-prev-entity get-prev-entity
                                 :create-event-fn to-update-event)
          delete-flow-map (assoc base-flow-map
                                 :flow-type :delete
                                 :create-event-fn to-delete-event)
          expected-events (map fake-event valid-entities)]

      (doseq [flow-map [create-flow-map
                        update-flow-map
                        delete-flow-map]]
          (is (= (assoc flow-map :events expected-events)
                 (#'flows.crud/create-events flow-map))
              (format "create-events shall properly handle %s flow type"
                      (:flow-type flow-map)))))))

(defn search-events
  [event-store
   ident
   timestamp
   event-type
   entity-id]
  (:data (query-string-search
          event-store
          {:search-query {:filter-map {:entity.id entity-id
                                       :event_type event-type}
                          :range {:timestamp {:gte timestamp}}}
           :ident ident
           :params {}})))

(defn mk-sighting [id]
  (assoc sighting-minimal
         :title (str "sighting " id)
         :id id
         :tlp "green"
         :groups ["groups"]))

(deftest crud-flow-test
  (helpers/with-properties
    ["ctia.store.es.event.refresh" "true"] ;; force refresh for events created in flow
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             sighting-ids (repeatedly 4 #(flows.crud/make-id :sighting))
             [sighting-id-1 sighting-id-2 sighting-id-3 sighting-id-4] sighting-ids
             store (atom {sighting-id-1 (mk-sighting sighting-id-1)
                          sighting-id-2 (mk-sighting sighting-id-2)
                          sighting-id-3 (mk-sighting sighting-id-3)
                          sighting-id-4 (mk-sighting sighting-id-4)})
             ident {:login "login"
                    :groups ["group1"]}
             event-store (helpers/get-store app :event)
             get-fn (fn [ids] (vals (select-keys @store ids)))
             update-fn (fn [patches]
                         (mapv
                          (fn [{:keys [id] :as patch}]
                            (let [[_old new]
                                  (swap-vals!
                                   store
                                   (fn [s]
                                     (when (contains? s id)
                                       (assoc s id patch))))]
                              (get new id)))
                          patches))
             check-update (fn [res now source-value expected]
                            (let [not-found-ids (set (:not-found res))]
                              (doseq [[id updated?] expected]
                                (is (= (not updated?)
                                       (contains? not-found-ids id)))
                                (is (= updated?
                                       (= source-value
                                          (:source (get @store id))))
                                    (format "the expected update result for %s is %s (%s)" id updated? (pr-str (get @store id))))
                                (is (= updated?
                                       (->> (search-events
                                             event-store
                                             ident
                                             now
                                             :record-updated
                                             id)
                                            first
                                            some?))
                                    (format "The expected update event for %s should be %s" id updated?)))))
             make-result #(select-keys % [:entities :not-found])
             update-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [now (jt/instant)
                                   entity-ids (seq (keys expected))
                                   docs (map (fn [id]
                                                  (assoc (mk-sighting id)
                                                         :source "updated"))
                                             entity-ids)
                                   res (flows.crud/update-flow
                                        :entity-type :sighting
                                        :services services
                                        :get-fn get-fn
                                        :realize-fn  ss/realize-sighting
                                        :update-fn update-fn
                                        :identity (map->Identity ident)
                                        :entities docs
                                        :long-id-fn identity
                                        :spec :new-sighting/map
                                        :get-success-entities :entities
                                        :make-result make-result)]
                               (check-update res now "updated" expected))))
             patch-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [now (jt/instant)
                                   entity-ids (seq (keys expected))
                                   patches (map (fn [id]
                                                  {:id id
                                                   :source "patched"})
                                                entity-ids)
                                   res (flows.crud/patch-flow
                                        :entity-type :sighting
                                        :get-fn get-fn
                                        :partial-entities patches
                                        :realize-fn  ss/realize-sighting
                                        :update-fn update-fn
                                        :patch-operation :replace
                                        :long-id-fn identity
                                        :services services
                                        :spec :new-sighting/map
                                        :get-success-entities :entities
                                        :identity (map->Identity ident)
                                        :make-result make-result)]
                               (check-update res
                                             now
                                             "patched"
                                             expected))))
             delete-fn (fn [ids]
                         (into {}
                               (map (fn [id]
                                      (array-map
                                       id
                                       (let [[old _new] (swap-vals! store dissoc id)]
                                         (contains? old id)))))
                               ids))
             delete-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [entity-ids (seq (keys expected))
                                   now (jt/instant)
                                   res
                                   (flows.crud/delete-flow
                                    :entity-type :sighting
                                    :get-fn get-fn
                                    :delete-fn delete-fn
                                    :entity-ids entity-ids
                                    :long-id-fn identity
                                    :services services
                                    :get-success-entities :entities
                                    :identity (map->Identity ident))
                                   deleted-events? (into {}
                                                         (map #(->> (search-events event-store
                                                                                   ident
                                                                                   now
                                                                                   :record-deleted
                                                                                   %)
                                                                    first
                                                                    some?
                                                                    (array-map %)))
                                                         entity-ids)]
                               (is (= expected (into {} res)))
                               (is (= expected deleted-events?)
                                   (str "flow shall return " expected)))))
             missing-id-1 (short-id->long-id (flows.crud/make-id "missing") services)
             missing-id-2 (short-id->long-id (flows.crud/make-id "missing") services)]
         (patch-flow
          "patch-flow patches existing entities and create events accordingly"
          {sighting-id-1 true sighting-id-2 true})
         (patch-flow
          "patche-flow patches entities and creates events only for existing entities when some are not found"
          {sighting-id-3 true missing-id-1 false missing-id-2 false})
         (update-flow
          "update-flow patches existing entities and create events accordingly"
          {sighting-id-1 true sighting-id-2 true})
         (update-flow
          "update-flow updates entities and creates events only for existing entities when some are not found"
          {sighting-id-3 true missing-id-1 false missing-id-2 false})
         (delete-flow "delete-flow deletes existing entities and create events accordingly"
                      {sighting-id-1 true sighting-id-2 true})
         (delete-flow "delete-flow must not create events for deleted entities"
                      {sighting-id-1 false sighting-id-2 false})
         (delete-flow "delete flow deletes entities and creates events only for existing entities when some are not found"
                      {sighting-id-3 true missing-id-1 false missing-id-2 false}))))))

(defn mk-fake-store
  [entity size]
  (into {}
        (map (fn [id] [id (mk-sighting id)]))
        (repeatedly size #(flows.crud/make-id entity))))

(deftest prev-entity-test
  (let [store (mk-fake-store :sighting 3)
        sighting-short-ids (keys store)
        sighting-long-ids (map #(str "http://localhost:3000/ctia/sighting/" %) sighting-short-ids)
        sighting-short-id-1 (first sighting-short-ids)
        sighting-long-id-1 (first sighting-long-ids)
        _ (assert (every? map? (vals store))
                  "fake-get-fn is not properly initialized, stopping test here")
        fake-get-fn (fn [ids] (map store ids))
        prev-entity-fn (flows.crud/prev-entity fake-get-fn sighting-short-ids)]
    (is (= (store sighting-short-id-1)
           (prev-entity-fn sighting-short-id-1))
        "generated prev-entity-fn shall properly return entities from short ids")
    (is (= (store sighting-short-id-1)
           (prev-entity-fn sighting-long-id-1))
        "generated prev-entity-fn shall properly return entities from long ids")
    (is (nil? (prev-entity-fn "not-found")))))

(deftest patch-entities-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)
           store (mk-fake-store :sighting 5)
           sighting-short-ids (keys store)
           patched-sighting-ids (take 3 sighting-short-ids)
           partial-entities (map #(array-map :id % :source "patched")
                                 patched-sighting-ids)
           not-found-patch {:id "not-found" :source "patched"}
           entities (conj partial-entities not-found-patch)
           patch-flow-map {:create-event-fn identity
                           :entities entities
                           :entity-type :indicator
                           :flow-type :update
                           :services services
                           :identity (map->Identity {:login "user1" :groups ["g1"]})
                           :store-fn identity
                           :get-prev-entity store
                           :patch-operation :replace}
           expected-entities (conj (map #(-> (store %)
                                             (assoc :source "patched")
                                             (dissoc :schema_version))
                                        patched-sighting-ids)
                                   not-found-patch)
           expected (assoc patch-flow-map :entities expected-entities)]
       (is (= expected
              (flows.crud/patch-entities patch-flow-map)))))))
