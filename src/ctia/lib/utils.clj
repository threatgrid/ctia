(ns ctia.lib.utils
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

(defn filter-out-creds [m]
  (reduce-kv (fn [acc k v]
               (if (re-matches #"(?i).*(key|pass|token|secret).*" (str k))
                 (assoc acc k "********")
                 (assoc acc k v)))
             m
             m))

(defn deep-filter-out-creds [m]
  (walk/prewalk #(if (map? %)
                   (filter-out-creds %)
                   %)
                m))

;; copied from log-helper.safe
(def to-obfuscate-pattern
  (let [keywords-to-obfuscate ["authorization"
                               "creds"
                               "key"
                               "basic-auth"
                               "pass"
                               "secret"
                               "tempfile"
                               "customerKey"
                               "response-url"
                               "token"]]
    (re-pattern (str "(?i).*("
                     (string/join "|" keywords-to-obfuscate)
                     ").*"))))

;; copied from log-helper.safe
(def to-obfuscate-keys
  #{"jwt" "authorization"})

;; copied from log-helper.safe
(defn obfuscate?
  [k v]
  (let [k-str (if (or (keyword? k) (string? k))
                (string/lower-case (name k))
                (do
                  ;; SHOULD not occurs except during development
                  ;; only a warn because it is a bug that do not affect the
                  ;; end-user.
                  (log/warnf "Some hash-map with key not a keyword nor string: %s."
                             (clojure.core/pr-str k))
                  (clojure.core/pr-str k)))]
    (cond
      (contains? to-obfuscate-keys k-str) (string? v)
      :else (some? (re-matches to-obfuscate-pattern k-str)))))

;; copied from log-helper.safe
(defn obfuscate-nested-map
  "Obfuscate a value recursively by keeping the structure and the type"
  [v]
  (walk/prewalk #(if (string? %) "********" %) v))

;; copied from log-helper.safe
#_ ;; commented out for now, using CTIA's above
(defn filter-out-creds
  "Given an hash-map obfuscate credentials."
  [m]
  (reduce-kv (fn [acc k v]
               (cond
                 (obfuscate? k v) (update acc k obfuscate-nested-map)
                 :else (assoc acc k v)))
             m
             m))

(defn safe-pprint [& xs]
  (->> xs
       (map deep-filter-out-creds)
       (apply clojure.pprint/pprint)))

(defn safe-pprint-str [& xs]
  (with-out-str (apply safe-pprint-str xs)))

;; copied from iroh-core.core
(defn clean-collection
  "Remove items in the collection that are equal to ::to-remove.
   If the resulting collection is empty, `::to-remove` is returned.
   The collection type is preserved."
  [c]
  (let [cleaned (->> c
                     (remove #(= % ::to-remove))
                     (into (empty c)))]
    (cond
      (and (seq c)
           (empty? cleaned)) ::to-remove
      (list? cleaned) (reverse cleaned) ;; into reverse the content of a list
      :else cleaned)))

;; copied from iroh-core.core
(defn map-filter
  "filter generalized to maps"
  [f m]
  (into {}
        (for [[k v] m :when (f v)] [k v])))

;; copied from iroh-core.core
(defn clean-map
  "Remove map entries whose value is not a map, satisfy the `f` predicate
   or is equal to ::to-remove. If the resulting map is empty `::to-remove`
   is returned."
  [f m]
  (let [cleaned (map-filter
                 (fn [x]
                   (if (map? x)
                     true
                     (and (not= ::to-remove x) (f x))))
                 m)]
    (if (and (seq m)
             (empty? cleaned))
      ::to-remove
      cleaned)))

;; copied from iroh-core.core
(defn deep-filter
  "Deeply nested filter. Filter on leaves only and remove the un-needed subtrees."
  [f m]
  (let [clean-result (fn [r]
                       (if (= r ::to-remove) {} r))]
    (->> m
         (walk/postwalk
          (fn [node]
            (cond
              (map? node) (clean-map f node)
              (and (not (map-entry? node))
                   (coll? node)) (clean-collection node)
              :else node)))
         clean-result)))

;; copied from iroh-core.core
(def deep-remove-nils
  "Remove nil values from a deep nested map recursively"
  (partial deep-filter some?))
