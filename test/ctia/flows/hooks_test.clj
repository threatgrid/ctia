(ns ctia.flows.hooks-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.flows.hooks-service :as hooks-svc]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [clojure.test :as t]))

(t/use-fixtures :each (t/join-fixtures [mth/fixture-schema-validation
                                        helpers/fixture-properties:clean
                                        es-helpers/fixture-properties:es-store
                                        helpers/fixture-ctia-fast]))

(def obj {:x "x" :y 0 :z {:foo "bar"}})

;; -----------------------------------------------------------------------------
;; Dummy Hook
(defrecord Dummy [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object]
    (update stored-object :dummy #(if (nil? %)
                                    name
                                    (str % " - " name))))
  (destroy [this] :noop))

(defn test-adding-dummy-hooks [add-hook!]
  (add-hook! :before-create (Dummy. "hook1"))
  (add-hook! :before-create (Dummy. "hook2"))
  (add-hook! :before-create (Dummy. "hook3")))

(t/deftest check-dummy-hook-order
  (let [app (helpers/get-current-app)
        {:keys [add-hook! apply-hooks init-hooks!]} (-> (helpers/get-service-map app :HooksService)
                                                        hooks-svc/lift-hooks-service-fns)]
    (test-adding-dummy-hooks add-hook!)
    (init-hooks!)
    (t/is (= (apply-hooks :entity obj
                          :hook-type  :before-create)
             (into obj {:dummy "hook1 - hook2 - hook3"})))
    (t/is (= (apply-hooks :entity obj
                          :hook-type  :after-create)
             obj))))

(t/deftest check-dummy-hook-read-only
  (let [app (helpers/get-current-app)
        {:keys [add-hook! apply-hooks init-hooks!
                shutdown! reset-hooks!]} (-> (helpers/get-service-map app :HooksService)
                                             hooks-svc/lift-hooks-service-fns)]
    (shutdown!)
    (reset-hooks!)
    (test-adding-dummy-hooks add-hook!)
    (init-hooks!)
    (t/is (= (apply-hooks :entity   obj
                          :hook-type   :before-create
                          :read-only?  true)
             obj))
    (t/is (= (apply-hooks :entity obj
                          :hook-type   :after-create
                          :read-only?  true)
             obj))))

;; -----------------------------------------------------------------------------
;; nil hook testing
(defrecord Nil [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object] nil)
  (destroy [this] :noop))

(defn test-adding-nil-hooks [add-hook!]
  (add-hook! :before-create (Nil. "nil1"))
  (add-hook! :before-create (Nil. "nil2"))
  (add-hook! :before-create (Nil. "nil3")))

(t/deftest check-nil-hook
  (let [app (helpers/get-current-app)
        {:keys [add-hook! apply-hooks init-hooks!
                shutdown! reset-hooks!]} (-> (helpers/get-service-map app :HooksService)
                                             hooks-svc/lift-hooks-service-fns)]
    (shutdown!)
    (reset-hooks!)
    (test-adding-nil-hooks add-hook!)
    (init-hooks!)
    (t/is (= (apply-hooks :entity obj
                          :hook-type :before-create)
             obj))
    (t/is (= (apply-hooks :entity obj
                          :hook-type :after-create)
             obj))))

;; -----------------------------------------------------------------------------
;; Memory Hook
(defrecord Memory [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object]
    (into stored-object {:previous prev-object}))
  (destroy [this] :noop))


(defn test-adding-memory-hooks [add-hook!]
  (add-hook! :before-create (Memory. "memory1"))
  (add-hook! :before-create (Memory. "memory2"))
  (add-hook! :before-create (Memory. "memory3")))

(t/deftest check-memory-hook
  (let [app (helpers/get-current-app)
        {:keys [add-hook! apply-hooks init-hooks!
                shutdown! reset-hooks!]} (-> (helpers/get-service-map app :HooksService)
                                             hooks-svc/lift-hooks-service-fns)
        memory {:y "y"}]
    (shutdown!)
    (reset-hooks!)
    (test-adding-memory-hooks add-hook!)
    (init-hooks!)
    (t/is (= (apply-hooks :entity obj
                          :prev-entity memory
                          :hook-type :before-create)
             (into obj {:previous {:y "y"}})))
    (t/is (= (apply-hooks :entity obj
                          :prev-entity memory
                          :hook-type   :after-create)
             obj))))
