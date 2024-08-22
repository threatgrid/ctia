(ns ctia.schemas.core)

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(do
     ~ddl
     ~spec-kw-ns
     (schema.core/defschema ~name-sym s/Any)))

(defmacro def-stored-schema [name-sym sch]
  `(schema.core/defschema ~name-sym ~sch))

(defmacro def-advanced-acl-schema [{:keys [name-sym
                                           ddl
                                           _spec-kw-ns
                                           _open?]}]
  `(do
     ~ddl
     (schema.core/defschema ~name-sym t/Any)))
