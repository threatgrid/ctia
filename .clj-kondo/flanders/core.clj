(ns flanders.core)

(defmacro def-enum-type [name _values & _opts]
  `(def ~name 'something))

(defmacro def-entity-type [name description & map-entries]
  `(do
     ~description
     ~map-entries
     (def ~name 'something)))

(defmacro def-eq [name value & opts]
  `(do
     ~value
     ~opts
     (def ~name 'something)))

(defmacro def-map-type [name map-entries & opts]
  `(do
     ~map-entries
     ~opts
     (def ~name 'something)))
