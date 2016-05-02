(ns ctia.flows.autoload
  (:require
   [leiningen.core.project :as p]
   [schema.core :as s]
   [ctia.flows.hooks :as h]
   [ctia.flows.hook-protocol :refer [Hook]]))

(defrecord ProxyJ [o]
  Hook
  (init [_] (doto (.init o)))
  (handle [_ stored-object prev-object]
    (h/from-java-handle o stored-object prev-object))
  (destroy [_] (doto (.destroy o))))

(s/defn add-record :- (s/eq nil)
  "Given a `record` symbol it load it "
  [hook-cls :- s/Symbol
   hook-type :- s/Keyword]
  (let [hook-obj (do (eval `(do
                              (require '[~(symbol
                                           (first (clojure.string/split (str hook-cls) #"/")))])
                              ~hook-cls)))]
    (if (satisfies? Hook hook-obj)
      (h/add-hook! hook-type hook-obj)
      (binding [*out* *err*]
        (println hook-cls " doesn't satisfies `ctia.flows.hooks/Hook` protcol!")))))

(s/defn add-java-class :- (s/eq nil)
  "Add a java class as Hook"
  [hook-cls :- s/Symbol
   hook-type :- s/Keyword]
  (let [hook-obj (eval `(new ~hook-cls))]
    (if (instance? ctia.Hook hook-obj)
      (h/add-hook! hook-type (ProxyJ. hook-obj))
      (binding [*out* *err*]
        (println hook-cls "isn't an instance of `ctia.Hook`!")))))

(s/defn load-hooks! :- (s/eq nil)
  [hooks :- [{s/Keyword s/Symbol}]]
  (doseq [[hook-type hook-classes] hooks]
    (doseq [hook-cls hook-classes]
      (if (.contains (str hook-cls) "/")
        (add-record hook-cls hook-type)
        (add-java-class hook-cls hook-type)))))

(s/defn autoload-hooks! :- (s/eq nil)
  "Should retrieve a list of Hook classes from `project.cljs`.
  All these classes must implement the `Hook` java interface."
  ([]
   (load-hooks!
    (:hooks-classes (p/read))))
  ([profiles :- [s/Keyword]]
   (load-hooks!
    (:hooks-classes (try (p/read "project.clj" profiles)
                         (finally
                           nil))))))
