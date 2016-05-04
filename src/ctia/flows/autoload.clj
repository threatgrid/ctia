(ns ctia.flows.autoload
  (:require [ctia.flows.from-java :as fj]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.properties :refer [properties]]
            [schema.core :as s]
            [clojure.string :as str]))

(defn- record
  "Given a `record` symbol, load it "
  [hook-type hook-cls hook-cls-sym]
  (let [hook-obj (eval `(do
                          (require '[~(symbol
                                       (first (clojure.string/split hook-cls
                                                                    #"/")))])
                          ~hook-cls-sym))]
    (assert (satisfies? Hook hook-obj)
            (str hook-cls " doesn't satisfies `ctia.flows.hooks/Hook` protcol!"))
    hook-obj))

(defn- java-class
  "Add a java class as Hook"
  [hook-type hook-cls hook-cls-sym]
  (let [hook-obj (eval `(new ~hook-cls-sym))]
    (assert (instance? ctia.Hook hook-obj)
            (str hook-cls "isn't an instance of `ctia.Hook`!"))
    (fj/->ProxyJ hook-obj)))

(defn- load-hook
  [hook-type hook-class]
  (let [hook-class-sym (symbol hook-class)]
    (if (.contains hook-class "/")
      (record hook-type hook-class hook-class-sym)
      (java-class hook-type hook-class hook-class-sym))))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks :- {s/Keyword [(s/protocol Hook)]}]
  (reduce (fn [accum [hook-type hook-classes-str]]
            (reduce (fn [inner-accum hook-class]
                      (update inner-accum hook-type conj (load-hook hook-type
                                                                    hook-class)))
                    accum
                    (str/split hook-classes-str #",")))
          hooks
          (get-in @properties [:ctia :hooks])))
