(ns flanders.core)

(defmacro def-enum-type [name _values & _opts]
  `(clojure.core/def ~name))

(defmacro def-entity-type [name description & map-entries]
  `(do
     ~description
     ~map-entries
     (clojure.core/def ~name)))

(defmacro def-eq [name value & opts]
  `(do
     ~value
     ~opts
     (clojure.core/def ~name)))

(defmacro def-map-type [name map-entries & opts]
  `(do
     ~map-entries
     ~opts
     (clojure.core/def ~name)))
