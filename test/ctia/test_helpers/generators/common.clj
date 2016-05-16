(ns ctia.test-helpers.generators.common
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [schema.experimental.complete :as sec]
            [schema.experimental.generators :as seg]))

(defn maybe [gen]
  (gen/frequency [[1 (gen/return nil)]
                  [2 gen]]))

(defn gen-str-3+ [gen-char]
  (gen/fmap (comp (partial apply str) flatten)
            (gen/tuple gen-char
                       gen-char
                       gen-char
                       (gen/vector gen-char))))

(def gen-char-alpha-lower (gen/fmap char
                                    (gen/choose 97 122)))

(def leaf-generators
  {java.util.Date
   ;; very simplistic randomized date
   (gen/fmap #(time/plus-n-weeks (time/now) %)
             gen/int)})

(defn generate-entity [schema]
  (seg/generator schema leaf-generators))

(defn complete [schema m]
  (sec/complete m schema {} leaf-generators))

(def gen-valid-time-tuple
  (gen/tuple (maybe (gen/fmap time/format-date-time
                              (get leaf-generators java.util.Date)))
             (maybe (gen/fmap time/format-date-time
                              (get leaf-generators java.util.Date)))))
