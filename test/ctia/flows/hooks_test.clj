(ns ctia.flows.hooks-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.flows.hooks :as h]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [clojure.test :as t]))

(t/use-fixtures :each (t/join-fixtures [mth/fixture-schema-validation
                                        helpers/fixture-properties:clean
                                        es-helpers/fixture-properties:es-store
                                        helpers/fixture-ctia-fast]))

;; FIXME port these tests to the new trapperkeeper hooks-service.
;; the problem at the moment is this namespace calls h/init!,
;; which was deleted. It was deleted because it also initialized hooks,
;; which I moved to the `start` method of hooks-service.
;; I can see several different ways forward:
;; - auto-initialize on `add-hook!`
;; - make Hook implementations into services
;;
;; Whatever the case, it would probably need `with-app-with-config`,
;; which isn't possible at the moment since trapperkeeper is tightly
;; managed by ctia.init.
(comment
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

(defn test-adding-dummy-hooks []
  (h/add-hook! :before-create (Dummy. "hook1"))
  (h/add-hook! :before-create (Dummy. "hook2"))
  (h/add-hook! :before-create (Dummy. "hook3")))

(t/deftest check-dummy-hook-order
  (test-adding-dummy-hooks)
  (h/init-hooks!)
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type  :before-create)
           (into obj {:dummy "hook1 - hook2 - hook3"})))
  (t/is (= (h/apply-hooks :entity  obj
                          :hook-type  :after-create)
           obj)))

(t/deftest check-dummy-hook-read-only
  (h/shutdown!)
  (h/reset-hooks!)
  (test-adding-dummy-hooks)
  (h/init-hooks!)
  (t/is (= (h/apply-hooks :entity   obj
                          :hook-type   :before-create
                          :read-only?  true)
           obj))
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type   :after-create
                          :read-only?  true)
           obj)))

;; -----------------------------------------------------------------------------
;; nil hook testing
(defrecord Nil [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object] nil)
  (destroy [this] :noop))

(defn test-adding-nil-hooks []
  (h/add-hook! :before-create (Nil. "nil1"))
  (h/add-hook! :before-create (Nil. "nil2"))
  (h/add-hook! :before-create (Nil. "nil3")))

(t/deftest check-nil-hook
  (h/shutdown!)
  (h/reset-hooks!)
  (test-adding-nil-hooks)
  (h/init-hooks!)
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :before-create)
           obj))
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :after-create)
           obj)))

;; -----------------------------------------------------------------------------
;; Memory Hook
(defrecord Memory [name]
  Hook
  (init [this] :noop)
  (handle [_ stored-object prev-object]
    (into stored-object {:previous prev-object}))
  (destroy [this] :noop))


(defn test-adding-memory-hooks []
  (h/add-hook! :before-create (Memory. "memory1"))
  (h/add-hook! :before-create (Memory. "memory2"))
  (h/add-hook! :before-create (Memory. "memory3")))

(t/deftest check-memory-hook
  (let [memory {:y "y"}]
    (h/shutdown!)
    (h/reset-hooks!)
    (test-adding-memory-hooks)
    (h/init-hooks!)
    (t/is (= (h/apply-hooks :entity   obj
                            :prev-entity memory
                            :hook-type   :before-create)
             (into obj {:previous {:y "y"}})))
    (t/is (= (h/apply-hooks :entity obj
                            :prev-entity memory
                            :hook-type   :after-create)
             obj))))
)
