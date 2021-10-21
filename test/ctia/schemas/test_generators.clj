(ns ctia.schemas.test-generators
  (:require [clojure.test.check.generators :as gen]
            [schema.core :as s]))

(declare map-schema)

(defn schema
  "A test.check generator for generating Schemas.

  (gen/generate (schema))
   ;=> Any
  "
  ([] (schema {}))
  ([{:keys [scalar-frequency recursive-frequency]
     :or {scalar-frequency 10
          recursive-frequency 1}}]
   (let [scalar-gen (gen/one-of
                      (map gen/return [s/Any s/Keyword s/Str]))]
     (gen/frequency
       [[scalar-frequency scalar-gen]
        [recursive-frequency (gen/recursive-gen
                               ;; recursive generators
                               (fn [inner-gen]
                                 (gen/one-of
                                   [(map-schema
                                      {:extra-keys-gen inner-gen
                                       :vals-gen inner-gen})]))
                               scalar-gen)]]))))

(defn map-schema
  "A test.check generator for generating map Schemas.

  (gen/generate (map-schema))
  ;=> {:-/m java.lang.String,
       #schema.core.OptionalKey{:k :y/+} java.lang.String,
       Any Keyword,
       Keyword Any}
  "
  ([] (map-schema {}))
  ([{:keys [extra-keys-gen vals-gen explicit-keys]
     :or {extra-keys-gen (schema)
          vals-gen (schema)}}]
   (gen/let [kws (if explicit-keys
                   (gen/return explicit-keys)
                   (gen/vector-distinct
                     gen/keyword-ns
                     {:max-elements 5}))
             extra-keys (gen/vector-distinct
                          extra-keys-gen
                          {:max-elements 2})
             kw-transformers (gen/vector
                               (gen/one-of
                                 (map gen/return [identity s/optional-key]))
                               (count kws))
             schemas (gen/vector
                       vals-gen
                       (+ (count kws)
                          (count extra-keys)))]
     (zipmap (concat (map #(%1 %2) kw-transformers kws)
                     extra-keys)
             schemas))))
