(ns ctia.flows.crud-test
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [java-time :as jt]
            [ctim.examples.sightings :refer [sighting-minimal]]
            [clojure.test :refer [deftest testing is]]
            [ctia.store :refer [query-string-search]]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [ctia.test-helpers.core :as helpers]
            [puppetlabs.trapperkeeper.app :as app]
            [ctia.flows.crud :as flows.crud]
            [ctia.lib.collection :as coll]))

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
          valid-entities [{:one 1 :owner "Huey"}
                          {:two 2 :owner "Dewey"}
                          {:three 3 :owner "Louie"}]
          entities-with-error (conj valid-entities {:error "something bad happened"})
          base-flow-map {:services {:ConfigService {:get-in-config (constantly true)}}
                         :identity ident
                         :entities entities-with-error}
          create-flow-map (assoc base-flow-map
                                 :flow-type :create
                                 :create-event-fn to-create-event)
          update-flow-map (assoc base-flow-map
                                 :flow-type :update
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
  [event-store ident timestamp entity-id]
  (:data (query-string-search
          event-store
          {:filter-map {:entity.id entity-id}
           :range {:timestamp {:gte timestamp}}}
          ident
          {})))

(defn mk-sighting [n]
  (assoc sighting-minimal
         :title (str "sighting-" n)
         :id (str "sighting-" n)
         :tlp "green"
         :groups [(str "groups" n)]))

(deftest delete-flow-test
  (helpers/with-properties
    ["ctia.store.es.event.refresh" "true"] ;; force refresh for events created in flow
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             store (atom {"sighting-1" (mk-sighting 1)
                          "sighting-2" (mk-sighting 2)
                          "sighting-3" (mk-sighting 3)
                          "sighting-4" (mk-sighting 4)})
             ident {:login "login"
                    :groups ["group1"]}
             event-store (helpers/get-store app :event)
             get-fn (fn [ids] (vals (select-keys @store ids)))
             delete-fn (fn [ids]
                         (into {}
                               (map (fn [id]
                                      (array-map
                                       id
                                       (let [[old _new] (swap-vals! store dissoc id)]
                                         (contains? old id)))))
                               ids))
             delete-flow (fn [msg expected]
                           (testing msg
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
                                                                                   %)
                                                                    first
                                                                    some?
                                                                    (array-map %)))
                                                         entity-ids)]
                               (is (= expected (into {} res)))
                               (is (= expected deleted-events?)
                                   (str "flow shall return " expected)))))]
         (delete-flow "delete-flow deletes existing entities and create events accordingly"
                      {"sighting-1" true "sighting-2" true})
         (delete-flow "delete-flow must not create events for deleted entities"
                      {"sighting-1" false "sighting-2" false})
         (delete-flow "delete flow deletes entities and creates events only for existing entities when some are not found"
                      {"sighting-3" true "missing1" false "missing2" false})
         ;; TODO test concurrent deletes
         )))))
