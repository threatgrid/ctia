(ns ctia.flows.hooks-test
  (:require [ctia.flows.hooks :as h]
            [clojure.test :as t]))

;; -----------------------------------------------------------------------------
;; Dummy Hook
(defrecord Dummy [name]
  h/Hook
  (init [this] :noop)
  (handle [_ type-name stored-object prev-object]
    (into stored-object {(keyword name) "passed"}))
  (destroy [this] :noop))

(defn test-adding-dummy-hooks []
  (h/add-hook! :before-create (Dummy. "hook1"))
  (h/add-hook! :before-create (Dummy. "hook2"))
  (h/add-hook! :before-create (Dummy. "hook3")))

(t/deftest check-dummy-hook-order
  (do
    (h/reset-hooks!)
    (test-adding-dummy-hooks)
    (t/is (= (h/apply-hooks "foo" {:x "x"} nil :before-create)
             {:x "x"
              :hook1 "passed"
              :hook2 "passed"
              :hook3 "passed"}))
    (t/is (= (h/apply-hooks "foo" {:x "x"} nil :before-send-to-event-chan)
             {:x "x"}))))

;; -----------------------------------------------------------------------------
;; nil hook testing
(defrecord Nil [name]
  h/Hook
  (init [this] :noop)
  (handle [_ type-name stored-object prev-object] nil)
  (destroy [this] :noop))

(defn test-adding-nil-hooks []
  (h/add-hook! :before-create (Nil. "nil1"))
  (h/add-hook! :before-create (Nil. "nil2"))
  (h/add-hook! :before-create (Nil. "nil3")))

(t/deftest check-nil-hook
  (do
    (h/reset-hooks!)
    (test-adding-nil-hooks)
    (t/is (= (h/apply-hooks "foo" {:x "x"} nil :before-create)
             {:x "x"}))
    (t/is (= (h/apply-hooks "foo" {:x "x"} nil :before-send-to-event-chan)
             {:x "x"}))))

;; -----------------------------------------------------------------------------
;; Memory Hook
(defrecord Memory [name]
  h/Hook
  (init [this] :noop)
  (handle [_ type-name stored-object prev-object]
    (into stored-object {:previous prev-object}))
  (destroy [this] :noop))


(defn test-adding-memory-hooks []
  (h/add-hook! :before-create (Memory. "memory1"))
  (h/add-hook! :before-create (Memory. "memory2"))
  (h/add-hook! :before-create (Memory. "memory3")))

(t/deftest check-memory-hook
  (let [memory {:y "y"}]
    (do
      (h/reset-hooks!)
      (test-adding-memory-hooks)
      (t/is (= (h/apply-hooks "foo" {:x "x"} memory :before-create)
               {:x "x" :previous {:y "y"}}))
      (t/is (= (h/apply-hooks "foo" {:x "x"} memory :before-send-to-event-chan)
               {:x "x"})))))
