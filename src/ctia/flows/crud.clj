(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating and deleting objects.

  (Cf. #159)."
  (:import java.util.UUID)
  (:require [ctia.flows.hooks :refer :all]))


(defn camel-case [txt]
  (->> (clojure.string/split txt #"-")
      (map clojure.string/capitalize)
      clojure.string/join))

(defn make-id [type-name _]
  (str type-name "-" (UUID/randomUUID)))

;; NOT USED MACRO BUT COULD BE USEFUL IF WE START TO BECOME LAZY
;; OR IF THE NUMBER OF HANDLER START TO BECOME TOO BIG
(defmacro realize
  "Launch the create flow

  1. realize the object provided a type, a login and a raw-object
     For this the function realize-X is called where X the type type-name
  2. Apply all hooks to the realized objects
  3. return the realized object modified by all hooks

  Saving the realized object should occurs after this call.
  "
  [hook-name type-name login object]
  `(let [id# (make-id ~(name type-name) ~object)
         realized# (~(symbol (str "realize-" (name type-name))) ~object id# ~login)
         hooked# (apply-hooks ~type-name realized# ~hook-name)]
     hooked#))
