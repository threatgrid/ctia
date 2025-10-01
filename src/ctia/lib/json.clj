(ns ctia.lib.json
  "JSON encoding configuration for CTIA."
  (:require [cheshire.generate :as generate])
  (:import [com.fasterxml.jackson.core JsonGenerator]))

(defn register-charsequence-encoder!
  "Register a custom encoder for CharSequence to serialize as strings.
   
   Fixes Jackson 2.18+ treating CharSequence as JavaBeans on Java 15+."
  []
  (generate/add-encoder
   CharSequence
   (fn [^CharSequence obj ^JsonGenerator json-generator]
     (.writeString json-generator (.toString obj)))))

(defn init!
  "Initialize JSON encoding configuration."
  []
  (register-charsequence-encoder!))
