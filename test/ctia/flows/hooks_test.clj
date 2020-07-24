(ns ctia.flows.hooks-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.flows.hooks-service :as hooks-svc]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [clojure.test :as t]
            [puppetlabs.trapperkeeper.app :as app]))

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

(defn test-adding-dummy-hooks [hooks-svc]
  (hooks-svc/add-hook! hooks-svc :before-create (Dummy. "hook1"))
  (hooks-svc/add-hook! hooks-svc :before-create (Dummy. "hook2"))
  (hooks-svc/add-hook! hooks-svc :before-create (Dummy. "hook3")))

(t/deftest check-dummy-hook-order
  (let [app (helpers/get-current-app)
        hooks-svc (app/get-service app :HooksService)]
    (test-adding-dummy-hooks hooks-svc)
    (hooks-svc/init-hooks! hooks-svc)
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :hook-type  :before-create})
             (into obj {:dummy "hook1 - hook2 - hook3"})))
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :hook-type  :after-create})
             obj))))

(t/deftest check-dummy-hook-read-only
  (let [app (helpers/get-current-app)
        hooks-svc (app/get-service app :HooksService)]
    (hooks-svc/shutdown! hooks-svc)
    (hooks-svc/reset-hooks! hooks-svc)
    (test-adding-dummy-hooks hooks-svc)
    (hooks-svc/init-hooks! hooks-svc)
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity   obj
                :hook-type   :before-create
                :read-only?  true})
             obj))
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :hook-type   :after-create
                :read-only?  true})
             obj))))

;; -----------------------------------------------------------------------------
;; nil hook testing
(defrecord Nil [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object] nil)
  (destroy [this] :noop))

(defn test-adding-nil-hooks [hooks-svc]
  (hooks-svc/add-hook! hooks-svc :before-create (Nil. "nil1"))
  (hooks-svc/add-hook! hooks-svc :before-create (Nil. "nil2"))
  (hooks-svc/add-hook! hooks-svc :before-create (Nil. "nil3")))

(t/deftest check-nil-hook
  (let [app (helpers/get-current-app)
        hooks-svc (app/get-service app :HooksService)]
    (hooks-svc/shutdown! hooks-svc)
    (hooks-svc/reset-hooks! hooks-svc)
    (test-adding-nil-hooks hooks-svc)
    (hooks-svc/init-hooks! hooks-svc)
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :hook-type :before-create})
             obj))
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :hook-type :after-create})
             obj))))

;; -----------------------------------------------------------------------------
;; Memory Hook
(defrecord Memory [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object]
    (into stored-object {:previous prev-object}))
  (destroy [this] :noop))


(defn test-adding-memory-hooks [hooks-svc]
  (hooks-svc/add-hook! hooks-svc :before-create (Memory. "memory1"))
  (hooks-svc/add-hook! hooks-svc :before-create (Memory. "memory2"))
  (hooks-svc/add-hook! hooks-svc :before-create (Memory. "memory3")))

(t/deftest check-memory-hook
  (let [app (helpers/get-current-app)
        hooks-svc (app/get-service app :HooksService)
        memory {:y "y"}]
    (hooks-svc/shutdown! hooks-svc)
    (hooks-svc/reset-hooks! hooks-svc)
    (test-adding-memory-hooks hooks-svc)
    (hooks-svc/init-hooks! hooks-svc)
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :prev-entity memory
                :hook-type :before-create})
             (into obj {:previous {:y "y"}})))
    (t/is (= (hooks-svc/apply-hooks
               hooks-svc
               {:entity obj
                :prev-entity memory
                :hook-type   :after-create})
             obj))))
