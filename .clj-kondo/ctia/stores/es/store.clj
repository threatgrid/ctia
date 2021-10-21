(ns ctia.stores.es.store)

(defmacro def-es-store
  [store-name
   _entity
   _stored-schema
   _partial-stored-schema]
  `(clojure.core/defrecord ~store-name [~(symbol "state")]))
