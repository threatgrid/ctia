(ns ctia.schemas.core)

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(do
     ~ddl
     ~spec-kw-ns
     (clojure.core/def ~name-sym)))

(defmacro def-stored-schema [name-sym _sch]
  `(clojure.core/def ~name-sym))

(defmacro def-advanced-acl-schema [{:keys [name-sym
                                           ddl
                                           _spec-kw-ns
                                           _open?]}]
  `(do
     ~ddl
     (clojure.core/def ~name-sym)))
