(ns ctia.flows.hooks-test
  (:require [ctia.flows.hooks :as h]
            [clojure.test :as t]))

(def obj {:x "x" :y 0 :z {:foo "bar"}})

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
    (h/init-hooks!)
    (t/is (= (h/apply-hooks "foo" obj nil :before-create)
             (into obj {:hook1 "passed"
                        :hook2 "passed"
                        :hook3 "passed"})))
    (t/is (= (h/apply-hooks "foo" obj nil :after-create)
             obj))
    (h/reset-hooks!)))

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
    (t/is (= (h/apply-hooks "foo" obj nil :before-create)
             obj))
    (t/is (= (h/apply-hooks "foo" obj nil :after-create)
             obj))))

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
      (t/is (= (h/apply-hooks "foo" obj memory :before-create)
               (into obj {:previous {:y "y"}})))
      (t/is (= (h/apply-hooks "foo" obj memory :after-create)
               obj))
      (h/reset-hooks!))))

;; Dummy Hook from Java
(defrecord DummyJ [o]
  h/Hook
  (init [this] (doto (.init o)))
  (handle [_ type-name stored-object prev-object]
    (h/from-java-handle o type-name stored-object prev-object))
  (destroy [this] (doto (.destroy o))))

(defn test-adding-dummy-hooks-from-java []
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ1")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ2")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ3"))))

(t/deftest check-dummy-hook-order-from-java
  (do
    (h/reset-hooks!)
    (test-adding-dummy-hooks-from-java)
    (h/init-hooks!)
    (t/is (= (h/apply-hooks "foo" obj nil :before-create)
             (into obj {"hookJ1 - initialized" "passed"
                        "hookJ2 - initialized" "passed"
                        "hookJ3 - initialized" "passed"})))
    (t/is (= (h/apply-hooks "foo" obj nil :after-create)
             obj))
    (h/reset-hooks!)))


;; Dummy Hook from Jar file
(defrecord DummyJ [o]
  h/Hook
  (init [this] (doto (.init o)))
  (handle [_ type-name stored-object prev-object]
    (h/from-java-handle o type-name stored-object prev-object))
  (destroy [this] (doto (.destroy o))))

(defn test-adding-dummy-hooks-from-jar []
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar1")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar2")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar3"))))

(t/deftest check-dummy-hook-order-from-jar
  (do
    (h/reset-hooks!)
    (test-adding-dummy-hooks-from-jar)
    (h/init-hooks!)
    (t/is (= (h/apply-hooks "foo" obj nil :before-create)
             (into obj {"hookJar1 - initialized" "passed-from-jar"
                        "hookJar2 - initialized" "passed-from-jar"
                        "hookJar3 - initialized" "passed-from-jar"})))
    (t/is (= (h/apply-hooks "foo" obj nil :after-create)
             obj))
    (h/reset-hooks!)))
