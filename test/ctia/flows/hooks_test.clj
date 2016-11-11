(ns ctia.flows.hooks-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.flows.from-java :as fj]
            [ctia.flows.hooks :as h]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.test-helpers
             [core :as helpers]
             [es :as es-helpers]]
            [clojure.test :as t]))

(t/use-fixtures :once (t/join-fixtures [mth/fixture-schema-validation
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

(defn test-adding-dummy-hooks []
  (h/add-hook! :before-create (Dummy. "hook1"))
  (h/add-hook! :before-create (Dummy. "hook2"))
  (h/add-hook! :before-create (Dummy. "hook3")))

(t/deftest check-dummy-hook-order
  (h/shutdown!)
  (h/reset-hooks!)
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

;; -----------------------------------------------------------------------------
;; Dummy Hook from Java

(defrecord DummyJ [o]
  Hook
  (init [this] (doto (.init o)))
  (handle [_ stored-object prev-object]
    (fj/from-java-handle o stored-object prev-object))
  (destroy [this] (doto (.destroy o))))

(defn test-adding-dummy-hooks-from-java []
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ1")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ2")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.Dummy "hookJ3"))))

(t/deftest check-dummy-hook-from-java
  (h/shutdown!)
  (h/reset-hooks!)
  (test-adding-dummy-hooks-from-java)
  (h/init-hooks!)
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :before-create)
           (into obj {"hookJ1 - initialized" "passed"
                      "hookJ2 - initialized" "passed"
                      "hookJ3 - initialized" "passed"})))
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :after-create)
           obj))
  (h/reset-hooks!))


;; Dummy Hook from Jar file
(defrecord DummyJ [o]
  Hook
  (init [this] (doto (.init o)))
  (handle [_ stored-object prev-object]
    (fj/from-java-handle o stored-object prev-object))
  (destroy [this] (doto (.destroy o))))

(defn test-adding-dummy-hooks-from-jar []
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar1")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar2")))
  (h/add-hook! :before-create (DummyJ. (new ctia.hook.DummyJar "hookJar3"))))

(t/deftest check-dummy-hook-from-jar
  (h/shutdown!)
  (h/reset-hooks!)
  (test-adding-dummy-hooks-from-jar)
  (h/init-hooks!)
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :before-create)
           (into obj {"hookJar1 - initialized" "passed-from-jar"
                      "hookJar2 - initialized" "passed-from-jar"
                      "hookJar3 - initialized" "passed-from-jar"})))
  (t/is (= (h/apply-hooks :entity obj
                          :hook-type :after-create)
           obj)))
