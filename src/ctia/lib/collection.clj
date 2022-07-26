(ns ctia.lib.collection)

(defn recast
  "given a source collection and the target one,
   cast target the same as source"
  [orig-coll new-coll]
  (cond
    (vector? orig-coll) (vec new-coll)
    (set? orig-coll) (set new-coll)
    :else new-coll))

(defn add-colls
  "given many collections as argument
   concat them keeping the first argument type"
  [& args]
  (let [new-coll
        (->> args
             (map #(or % []))
             (reduce into))]
    (recast (first args) new-coll)))

(defn remove-colls
  "given many collections as argument
   remove items on a from b successively"
  [& args]
  (let [new-coll
        (reduce
         (fn [a b]
           (remove (or (set b) #{})
                   (or a []))) args)]
    (recast (first args) new-coll)))

(defn replace-colls
  "given many collections as argument
   replace a from b successively"
  [& args]
  (let [new-coll (last args)]
    (recast (first args) new-coll)))

(defn fmap
  "TODO deprecate in favor of update-vals after migrating to clojure 1.11.x"
  [f m]
  (into {}
        (for [[k v] m]
          [k (f v)])))

;; Backport from clojure 1.11
(defn update-vals
  "m f => {k (f v) ...}
  Given a map m and a function f of 1-argument, returns a new map where the keys of m
  are mapped to result of applying f to the corresponding values of m."
  {:added "1.11"}
  [m f]
  (with-meta
    (persistent!
     (reduce-kv (fn [acc k v] (assoc! acc k (f v)))
                (if (instance? clojure.lang.IEditableCollection m)
                  (transient m)
                  (transient {}))
                m))
    (meta m)))

;; Backport from clojure 1.11
(defn update-keys
  "m f => {(f k) v ...}
  Given a map m and a function f of 1-argument, returns a new map whose
  keys are the result of applying f to the keys of m, mapped to the
  corresponding values of m.
  f must return a unique key for each key of m, else the behavior is undefined."
  {:added "1.11"}
  [m f]
  (let [ret (persistent!
             (reduce-kv (fn [acc k v] (assoc! acc (f k) v))
                        (transient {})
                        m))]
    (with-meta ret (meta m))))
