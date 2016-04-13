(ns ctia.flows.autoload
  (:require
   [leiningen.core.project :as p]
   [ctia.flows.hooks :as h]))

(defrecord ProxyJ [o]
  h/Hook
  (init [_] (doto (.init o)))
  (handle [_ type-name stored-object prev-object]
    (h/from-java-handle o type-name stored-object prev-object))
  (destroy [_] (doto (.destroy o))))

(defn add-record
  "Given a `record` symbol it load it "
  [hook-cls hook-type]
  (let [hook-obj (do (eval `(do
                              (require '[~(symbol
                                           (first (clojure.string/split (str hook-cls) #"/")))])
                              ~hook-cls)))]
    (if (satisfies? h/Hook hook-obj)
      (h/add-hook! hook-type hook-obj)
      (binding [*out* *err*]
        (println hook-cls " doesn't satisfies `ctia.flows.hooks/Hook` protcol!")))))

(defn add-java-class [hook-cls hook-type]
  (let [hook-obj (eval `(new ~hook-cls))]
    (cond
      (instance? ctia.Hook hook-obj) (h/add-hook! hook-type (ProxyJ. hook-obj))
      :else (binding [*out* *err*]
              (println "X isn't an instance of `ctia.Hook`!")))))

(defn load-hooks! [hooks]
  (doseq [[hook-type hook-classes] hooks]
    (doseq [hook-cls hook-classes]
      (if (.contains (str hook-cls) "/")
        (add-record hook-cls hook-type)
        (add-java-class hook-cls hook-type)))))

(defn autoload-hooks!
  "Should retrieve a list of Hook classes from `project.cljs`.
  All these classes must implement the `Hook` java interface."
  ([] (load-hooks! (:hooks-classes (p/read))))
  ([profiles] (load-hooks! (:hooks-classes (p/read "project.clj" profiles)))))
