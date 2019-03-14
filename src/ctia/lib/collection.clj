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

(defn fmap [f m]
  (into {}
        (for [[k v] m]
          [k (f v)])))
