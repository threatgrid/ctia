(ns ctia.lib.schema
  (:require [schema.core :as s]
            [schema.spec.collection :as collection]
            [schema.spec.core :as spec :include-macros true]))

;; Custom atom schema because schema.core doesn't work with
;; durable-atom.  The fix is in the atom? pred, everything else is
;; copied from schema.core. Understands both regular atoms and
;; durable-atoms, with a bias towards the former.

(defn- atom? [x]
  (instance? clojure.lang.IAtom x))

(defrecord Atomic [schema]
  s/Schema
  (spec [this]
    (collection/collection-spec
     (spec/simple-precondition this atom?)
     atom
     [(collection/one-element true schema (fn [item-fn coll] (item-fn @coll) nil))]
     (fn [_ xs _] (atom (first xs)))))
  (explain [this] (list 'atom (s/explain schema))))

(defn atom
  "An atom containing a value matching 'schema'."
  [schema]
  (->Atomic schema))
