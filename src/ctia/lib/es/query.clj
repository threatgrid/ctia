(ns ctia.lib.es.query)

(defn bool
  "Boolean Query"
  [opts]
  {:bool opts})

(defn filtered
  "Filtered query"
  [opts]
  {:filtered opts})

(defn nested
  "Nested document query"
  [opts]
  {:nested opts})

(defn term
  "Term Query"
  ([key values] (term key values nil))
  ([key values opts]
   (merge { (if (coll? values) :terms :term) (hash-map key values) }
          opts)))

(defn terms
  "Terms Query"
  ([key values] (terms key values nil))
  ([key values opts]
   (term key values opts)))
