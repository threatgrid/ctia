(ns ctia.dev.split-tests
  (:require [circleci.test :as t]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.pprint :as pprint]))

; Algorithm By Mark Dickinson https://stackoverflow.com/a/2660138
(defn partition-fairly
  "Partition coll into n chunks such that each chunk's
  count is within one of eachother. Puts its larger chunks first.
  Returns a vector of chunks (vectors)."
  [n coll]
  {:pre [(integer? n)]
   :post [(or (empty? %)
              (let [fc (count (first %))]
                (every? #{fc (dec fc)} (map count %))))
          (= coll (apply concat %))]}
  ;TODO make lazier (use partition with overlapping steps to iterate
  ; over `indices`)
  (let [coll (vec coll)
        q (quot (count coll) n)
        r (rem (count coll) n)
        indices (mapv #(+ (* q %)
                          (min % r))
                      (range (inc n)))]
    (mapv #(subvec coll
                   (indices %)
                   (indices (inc %)))
          (range n))))

(defn read-env-config
  "Returns [${CTIA_THIS_SPLIT} ${CTIA_NSPLITS}] as Clojure data."
  []
  {:post [((every-pred vector?
                       (comp #{2} count)
                       #(every? integer? %))
           %)
          (let [[this-split total-splits] %]
            (< -1 this-split total-splits))]}
  (let [split (some-> (System/getenv "CTIA_THIS_SPLIT")
                      edn/read-string)
        nsplits (some-> (System/getenv "CTIA_NSPLITS")
                        edn/read-string)]
    (assert (or (every? number? [split nsplits])
                (every? nil? [split nsplits]))
            (str "Must specify both CTIA_NSPLITS and CTIA_THIS_SPLIT "
                 [split nsplits]))
    (if split
      [split nsplits]
      ; default: this is the first split of total 1 split. (ie., run everything)
      [0 1])))

(defn nses-for-this-build [[this-split total-splits] nsyms]
  (let [{entity-nsyms true
         non-entity-nsyms false} (group-by (fn [nsym]
                                            (assert (symbol? nsym))
                                            (boolean
                                              (str/starts-with?
                                                (name nsym)
                                                "ctia.entity")))
                                          nsyms)
        ;stabilize order across builds
        entity-nsyms (sort entity-nsyms)
        non-entity-nsyms (sort non-entity-nsyms)

        ;calculate all splits
        entity-splits (partition-fairly total-splits entity-nsyms)
        non-entity-splits (partition-fairly total-splits non-entity-nsyms)
        all-splits (map (fn [n]
                          (concat (nth entity-splits n)
                                  (nth non-entity-splits n)))
                        (range total-splits))]
    ;select this split
    (nth all-splits this-split)))

;Derived from https://github.com/circleci/circleci.test/blob/master/src/circleci/test.clj
;
;Copyright © 2017-2020 Circle Internet Services and contributors
;
;Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
;
; See [[LICENSE]] for a copy of EPL 1.0.
(defn dir
  "Same usage as circleci.test/dir. To split tests, bind
  environment variable CTIA_SPLIT_TESTS to a string containing a vector
  pair [n m], where m is the number of splits, and n identifies the
  current split in `(range m)`.
  
  eg., CTIA_SPLIT_TESTS=\"[0 2]\" lein test       ; run the first half of the tests
  eg., CTIA_SPLIT_TESTS=\"[1 2]\" lein test       ; run the second half of the tests
  "
  ([a1 a2 a3 & rst]
   (println "ctia.dev.split-tests/dir does not support extra arguments:" (vec (cons a3 rst)))
   (println "`lein test` only supports a selector, defaulting to `lein test :default`")
   (println "For a `lein test :only ...` equivalent using, use `lein tests ns1 ns2` without setting CTIA_SPLIT_TESTS")
   (System/exit 1))
  ([dirs-str] (dir dirs-str ":default"))
  ([dirs-str selector-str]
   (let [;; This function is designed to be used with Leiningen aliases only, since
         ;; adding :project/test-dirs to an alias will pass in data from the project
         ;; map as an argument; however it passes it in as a string.
         _ (when-not (try (coll? (read-string dirs-str)) (catch Exception _))
             (binding [*out* *err*]
               (println "Please see the readme for usage of this function.")
               (System/exit 1)))
         [this-split total-splits :as split-config] (read-env-config)
         all-nses (vec (@#'t/nses-in-directories (read-string dirs-str)))
         nses (vec (nses-for-this-build split-config all-nses))
         ;; Note: changed from circleci.test: removed :reload for more reliable tests
         _ (apply require #_:reload nses)
         selector (@#'t/lookup-selector (t/read-config!) (read-string selector-str))
         _ (if (#{[0 1]} split-config)
             (println "[ctia.dev.split-tests] Running all tests")
             (do 
               (println "[ctia.dev.split-tests] Splitting tests. Reproduce locally with:")
               (printf "[ctia.dev.split-tests] $ CTIA_SPLIT_TESTS=[%s,%s] lein split-test %s\n"
                       this-split
                       total-splits
                       selector-str)
               (println
                 (str "[ctia.dev.split-tests] "
                      "This is chunk " (inc this-split) " of " total-splits " testing "
                      (count nses) " of " (count all-nses) " test namespaces: "
                      nses))))
         summary (@#'t/run-selected-tests selector nses)]
     (System/exit (+ (:error summary) (:fail summary))))))
