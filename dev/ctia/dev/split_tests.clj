(ns ctia.dev.split-tests
  (:require [circleci.test :as t]
            [clojure.data.priority-map :refer [priority-map-keyfn]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.set :as set]
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
  "Returns [${CTIA_THIS_SPLIT} ${CTIA_NSPLITS}] as Clojure data.
  If either is not defined, returns [0 1]. This allows $CTIA_NSPLITS
  to be defined globally in a build, and jobs can opt-in to
  splitting."
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
    (if (and split nsplits)
      [split nsplits]
      ; default to the first split of total 1 split. (ie., run everything)
      [0 1])))

(defn slow-namespace? [nsym]
  {:pre [(symbol? nsym)]}
  (or
    (and
      (str/starts-with?
        (name nsym)
        "ctia.entity")
      (not
        ('#{ctia.entity.entities-test
            ctia.entity.event.obj-to-event-test
            ctia.entity.web-test}
          nsym)))
    (str/starts-with?
      (name nsym)
      "ctia.task.migration")
    (str/starts-with?
      (name nsym)
      "ctia.http.routes.graphql")))

(defn this-split-using-slow-namespace-heuristic [[this-split total-splits] nsyms]
  (let [;stabilize order across builds
        groups (->> nsyms
                    (group-by
                      (fn [nsym]
                        {:post [(number? %)]}
                        (if (slow-namespace? nsym) 
                          0
                          1)))
                    ;; slow namespaces first
                    sort
                    (map second)
                    (map sort))

        groups-splits (->> groups
                           (map (fn [group]
                                  (partition-fairly total-splits group))))
        ;calculate all splits
        all-splits (->> (range total-splits)
                        (mapv (fn [n]
                                {:post [(sequential? %)]}
                                (mapcat #(nth % n)
                                        groups-splits))))]
    (assert (= (sort nsyms)
               (sort (apply concat all-splits))))
    ;select this split
    (nth all-splits this-split)))

(defn this-split-using-load-balancing [timings [this-split total-splits] nsyms]
  {:pre [(map? timings)]
   :post [(vector? %)]}
  (let [extra-namespaces (set/difference (set nsyms)
                                         (set (keys timings)))
        ;; algorithm: always allocate new work to the fastest job, and process
        ;;            work from fastest to slowest
        splits (loop [so-far (apply priority-map-keyfn
                                    (juxt :duration :id)
                                    (mapcat (fn [split]
                                              [split {:id split
                                                      :duration 0
                                                      :nsyms []}])
                                            (range total-splits)))
                      [[elapsed-ns nsym :as fastest] & fastest-to-slowest]
                      (->> (concat timings
                                   (zipmap extra-namespaces (repeat ##Inf)))
                           (map (fn [[nsym {:keys [elapsed-ns]}]]
                                  {:pre [(simple-symbol? nsym)
                                         (nat-int? elapsed-ns)]}
                                  [elapsed-ns nsym]))
                           sort)]
                 (assert (seq so-far))
                 (if (not fastest)
                   so-far
                   (let [fastest-split-number (key (first so-far))
                         _ (assert (<= 0 fastest-split-number))
                         _ (assert (< fastest-split-number total-splits))]
                     (recur
                       (-> so-far
                           (update fastest-split-number
                                   #(-> %
                                        (update :duration + elapsed-ns)
                                        (update :nsyms conj nsym))))
                       fastest-to-slowest))))]
    (assert (= (sort nsyms)
               (sort (mapcat :nsyms (vals splits)))))
    ;; return sorted from fastest-to-slowest
    (:nsyms (get splits this-split))))

(defn nses-for-this-build [split-info nsyms]
  (if-some [timings (some-> (io/resource "ctia_test_timings.edn")
                            slurp
                            read-string)]
    (do (println "[ctia.dev.split-tests] Splitting with load balancing via dev-resources/ctia_test_timings.edn")
        (this-split-using-load-balancing timings split-info nsyms))
    (do (println "[ctia.dev.split-tests] Splitting via `slow-namespace?` heuristic")
        (this-split-using-slow-namespace-heuristic split-info nsyms))))

(defn wait-docker []
  (when (System/getenv "CTIA_WAIT_DOCKER")
    (println "[ctia.dev.split-tests] Waiting for docker...")
    ; Wait ES
    (sh/sh "bash" "-c" "until curl http://127.0.0.1:9200/; do sleep 1; done")
    ; Wait Kafka
    (sh/sh "bash" "-c" "until echo dump | nc 127.0.0.1 2181 | grep brokers; do sleep 1; done")
    (println "[ctia.dev.split-tests] Docker initialized.")))

;Derived from https://github.com/circleci/circleci.test/blob/master/src/circleci/test.clj
;
;Copyright © 2017-2020 Circle Internet Services and contributors
;
;Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
;
; See [[LICENSE]] for a copy of EPL 1.0.
(defn dir
  "Same usage as circleci.test/dir. To split tests, bind
  environment variables CTIA_THIS_SPLIT=n CTIA_NSPLITS=m,
  where m is the number of splits, and n identifies the
  current split in `(range m)`.
  
  eg., CTIA_THIS_SPLIT=0 CTIA_NSPLITS=2 lein test       ; run the first half of the tests
  eg., CTIA_THIS_SPLIT=1 CTIA_NSPLITS=2 lein test       ; run the second half of the tests
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
         _ (when (seq nses)
             ;; Note: changed from circleci.test: removed :reload for more reliable tests
             (apply require #_:reload nses))
         config (t/read-config!)
         selector (@#'t/lookup-selector config (read-string selector-str))
         _ (if (#{[0 1]} split-config)
             (println "[ctia.dev.split-tests] Running all tests")
             (do 
               (println "[ctia.dev.split-tests] Splitting tests. Reproduce locally with:")
               (printf "[ctia.dev.split-tests] $ CTIA_THIS_SPLIT=%s CTIA_NSPLITS=%s lein split-test %s\n"
                       this-split
                       total-splits
                       selector-str)
               (println
                 (str "[ctia.dev.split-tests] "
                      "This is chunk " (inc this-split) " of " total-splits " testing "
                      (count nses) " of " (count all-nses) " test namespaces: "
                      nses))))
         _ (wait-docker)
         summary (if (seq nses)
                   (@#'t/run-selected-tests selector nses config)
                   (do
                     (println "\nNo tests to run.")
                     (shutdown-agents)
                     (System/exit 1)))]
     (shutdown-agents)
     (System/exit (+ (:error summary) (:fail summary))))))
