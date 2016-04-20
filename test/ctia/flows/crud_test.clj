(ns ctia.flows.crud-test
  (:require [ctia.flows.crud :as crud]
            [ctia.flows.hooks :as h]
            [schema.core :as s]
            [clojure.test :as t]))


(s/defschema Model
  {:string s/Str
   :integer s/Num})

(s/def object
  {:string "x"
   :integer 0})

(def store (atom {}))


(t/deftest test-create-flow
  (do
    (h/reset-hooks!)
    (reset! store {})
    (crud/create-flow :model Model
                      :realize-fn #(into %1 {:id %2 :owner %3})
                      :store-fn #(reset! store %)
                      :object-type :model
                      :login "login"
                      :object object)
    (t/is (= (dissoc @store :id)
             (into (dissoc object :id)
                   {:owner "login"})))))

(t/deftest test-update-flow-without-hook
  (do
    (h/reset-hooks!)
    (reset! store {})
    (crud/update-flow :model Model
                      :get-fn (fn [_] @store)
                      :realize-fn #(into %1 {:id %2 :owner %3})
                      :store-fn #(reset! store %)
                      :object-type :model
                      :login "login"
                      :object object)
    (t/is (= (dissoc @store :id)
             (into (dissoc object :id)
                   {:owner "login"})))))

(t/deftest test-update-flow-without-hook
  (do
    (h/reset-hooks!)
    (let [stored-object (into object {:id "model-id"
                                      :owner "nobody"})
          old {:old "object"}]
      (reset! store old)
      (crud/update-flow :model Model
                        :get-fn (fn [_] @store)
                        :realize-fn #(into %1 {:id %2 :owner %3 :old %4})
                        :update-fn #(swap! store assoc :new-value %)
                        :object-type :model
                        :id (:id stored-object)
                        :login "login"
                        :object stored-object)
      (t/is (= @store
               (into old
                     {:new-value {:string "x"
                                  :integer 0
                                  :id "model-id"
                                  :owner "login"
                                  :old {:old "object"}}}))))))
