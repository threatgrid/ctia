(ns ctia.lib.compojure.api.core)

(defmacro context [& args]
  `(list ~@args))
