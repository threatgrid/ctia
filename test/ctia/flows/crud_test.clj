(ns ctia.flows.crud-test
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [clj-momo.test-helpers.core]
            [clojure.test :refer [deftest testing is]]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [ctia.flows.crud :as flows.crud]
            [ctia.lib.collection :as coll]))

(deftest deep-merge-with-add-colls-test
  (let [fixture {:foo {:bar ["one" "two" "three"]
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar ["one" "two" "three" "four"]
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with coll/add-colls
                                fixture
                                {:foo {:bar ["four"]}})))))

(deftest deep-merge-with-remove-colls-test
  (let [fixture {:foo {:bar #{"one" "two" "three"}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar #{"one" "three"}
                      :lorem ["ipsum" "dolor"]}}
               (deep-merge-with coll/remove-colls
                                fixture
                                {:foo {:bar ["two"]}})))))

(deftest deep-merge-with-replace-colls-test
  (let [fixture {:foo {:bar {:foo {:bar ["something" "or" "something" "else"]}}
                       :lorem ["ipsum" "dolor"]}}]
    (is (deep= {:foo {:bar {:foo {:bar ["else"]}}
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
  (testing "flow-type :update should preserve the entity owner"
    (let [create-update-event (fn [entity _ _ owner]
                                {:owner owner
                                 :entity entity})
          entities [{:one 1 :owner "Huey"}
                    {:two 2 :owner "Dewey"}
                    {:three 3 :owner "Louie"}]
          login "test-user"
          flow-map {:services        {:ConfigService {:get-in-config (constantly true)}}
                    :create-event-fn create-update-event
                    :identity        (map->Identity {:login login})
                    :flow-type       :update
                    :entities        entities}
          expected-events (map #(array-map :owner login :entity %)
                               entities)]
      (is (= (assoc flow-map :events expected-events)
             (#'flows.crud/create-events flow-map))))))
