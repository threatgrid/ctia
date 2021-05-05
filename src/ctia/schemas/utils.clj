(ns ctia.schemas.utils
  (:require [clojure.set :as set]
            [schema-tools.walk :as sw]
            [schema-tools.util :as stu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(defn recursive-open-schema-version
  "walk a schema and replace all instances of schema version
   with an open string schema"
  [s]
  (sw/walk
   (fn [x]
     (if (and
          (instance? clojure.lang.IMapEntry x)
          (= :schema_version (:k (first x))))
       [(first x) s/Str]
       (recursive-open-schema-version x)))
   identity
   s))

;; fns below are backported from schema-tools master
;; from https://github.com/metosin/schema-tools/blob/master/src/schema_tools/core.cljc#L266
;; TODO those can be removed once we use 0.10.6

;; TODO delete me
(defn optional-keys
  "Makes given map keys optional. Defaults to all keys."
  ([m] (st/optional-keys m))
  ([m ks] (st/optional-keys m ks)))

;; TODO delete me
(defn optional-keys-schema
  "Walks a schema making all keys optional in Map Schemas."
  [schema]
  (st/optional-keys-schema schema))

(s/defschema SpecificKey
  (s/pred s/specific-key?))

(s/defn service-subgraph :- (s/pred map?)
  "Returns a subgraph of a Trapperkeeper service graph value.
  If any required levels are missing, throws an exception.
  
  (service-subgraph
    {:ConfigService {:get-in-config <...>
                     :get-config <...>}
     :FooService {:f1 <...>
                  :f2 <...>}
     :BarService {:b1 <...>}}
    {:ConfigService #{:get-config}
     :FooService #{:f2 (s/optional-key :f3)}
     (s/optional-key :MissingService) #{:m1}})
  ;=> {:ConfigService {:get-config <...>}
  ;    :FooService {:f2 <...>}}
  "
  [graph :- (s/pred map?)
   selectors :- {SpecificKey #{SpecificKey}}]
  {:pre [(map? graph)]}
  (into {}
        (map (fn [[service-kw fn-kws]]
               (assert (s/specific-key? service-kw)
                       (pr-str service-kw))
               (assert (simple-keyword? (s/explicit-schema-key service-kw))
                       (s/explicit-schema-key service-kw))
               (assert (set? fn-kws))
               (let [gval (get graph (s/explicit-schema-key service-kw))]
                 (if-not (map? gval)
                   (when-not (s/optional-key? service-kw)
                     (throw (ex-info (str "Missing service: " service-kw) {})))
                   (let [service-fns (into {}
                                           (map (fn [fn-kw]
                                                  (assert (simple-keyword? (s/explicit-schema-key fn-kw))
                                                          (s/explicit-schema-key fn-kw))
                                                  (if-some [svc-fn (get gval (s/explicit-schema-key fn-kw))]
                                                    {(s/explicit-schema-key fn-kw) svc-fn}
                                                    (when-not (s/optional-key? fn-kw)
                                                      (throw (ex-info (str "Missing " service-kw " service function: "
                                                                           (s/explicit-schema-key fn-kw))
                                                                      {}))))))
                                           fn-kws)]
                     {(s/explicit-schema-key service-kw) service-fns})))))
        selectors))

(s/defn service-subgraph-from-schema :- (s/pred map?)
  "Given a schema describing a Trapperkeeper graph,
  returns just the elements in graph mentioned
  in the schema as 'explicit keys' using service-subgraph.
  Throws an exception if any levels are missing.
  
  (service-subgraph-from-schema
    {:ConfigService {:get-in-config (fn [...] ...)
                     :get-config (fn [...] ...)}
     :FooService {:f1 (fn [...] ...)
                  :f2 (fn [...] ...)}
     :BarService {:b1 (fn [...] ...)}}
    {:ConfigService {:get-in-config (s/=> ...)}
     (s/optional-key :FooService) {:f1 (s/=> ...)
                                   (s/optional-key :f2) (s/=> ...)}})
  ;=> {:ConfigService {:get-in-config (fn [...] ...)}
  ;    :FooService {:f1 (fn [...] ...)
  ;                 :f2 (fn [...] ...)}}
  "
  [graph
   schema :- (s/protocol s/Schema)]
  {:pre [(map? graph)
         (map? schema)]}
  (service-subgraph
    graph
    ;; this could be factored out a la schema.coerce/coercer for
    ;; better performance.
    (into {}
          (comp (filter (comp s/specific-key? key))
                (map (fn [[service-kw service-fns]]
                       {:pre [(map? service-fns)]}
                       [service-kw
                        (into #{}
                              (filter s/specific-key?)
                              (keys service-fns))])))
          schema)))

(s/defn service-subschema :- (s/protocol s/Schema)
  "Given a schema shaped like a Trapperkeeper service graph, selects
  the specified services and their optionality from graph. Throws an
  exception on selected service functions that don't occur in graph.
  
  (service-subschema
    {:ConfigService {:get-in-config (s/=> ...)
                     (s/optional-key :get-config) (s/=> ...)}
     (s/optional-key :FooService) {:f1 (s/=> ...)
                                   :f2 (s/=> ...)}
     :BarService {:b1 (s/=> ...)}}
    {:ConfigService #{:get-config}
     :FooService #{:f2 :f3}
     :MissingService #{:m1}})
  ;=> {:ConfigService {(s/optional-keys :get-config) (s/=> ...)}
  ;    (s/optional-key :FooService) {:f2 (s/=> ...)}}
  "
  [graph :- (s/protocol s/Schema)
   selectors :- {(s/pred simple-keyword?)
                 #{(s/pred simple-keyword?)}}]
  {:pre [(map? graph)]}
  (into {}
        (map (fn [[service-kw fn-kws]]
               (assert (keyword? service-kw)
                       (pr-str service-kw))
               (assert (set? fn-kws))
               (let [service-fns (some-> (st/get-in graph [service-kw])
                                         (st/select-keys fn-kws))]
                 (when (not= (count service-fns)
                             (count fn-kws))
                   (throw (ex-info (str "Missing service functions for "
                                        service-kw ": "
                                        (-> (set/difference
                                              (set fn-kws)
                                              (->> service-fns keys (map s/explicit-schema-key)))
                                            sort
                                            vec))
                                   {})))
                 (when service-fns
                   {service-kw service-fns}))))
        selectors))

(s/defn open-service-schema :- (s/protocol s/Schema)
  "Given a schema shaped like a Trapperkeeper service graph, 
  conjoins (s/pred simple-keyword?) {(s/pred simple-keyword?) (s/pred ifn?)}
  to the first layer and {(s/pred simple-keyword?) (s/pred ifn?)} to the second layers.

  (open-service-schema
    {:ConfigService {:get-in-config (s/=> ...)}
     :FooService {:f1 (s/=> ...)}})
  ;=> {:ConfigService {:get-in-config (s/=> ...)
  ;                    (s/pred simple-keyword?) (s/pred ifn?)}
  ;    :FooService {:f1 (s/=> ...)
  ;                 (s/pred simple-keyword?) (s/pred ifn?)}
  ;    (s/pred simple-keyword?) {(s/pred simple-keyword?) (s/pred ifn?)}}
  "
  [s :- (s/protocol s/Schema)]
  {:pre [(map? s)]}
  (let [open-service-fns {(s/pred simple-keyword?) (s/pred ifn?)}]
    (-> (into {}
              (map (fn [[k v]]
                     {:pre [(map? v)]}
                     [k (conj v open-service-fns)]))
              s)
        (conj {(s/pred simple-keyword?) open-service-fns}))))

(defn select-all-keys
  "Like st/select-keys but throws an exception if any keys are missing."
  [schema ks]
  (let [res (st/select-keys schema ks)
        missing-keys (into #{}
                           ;; Note: st/key-in-schema would be more direct, but it's private
                           (remove #(st/get-in res [%]))
                           ks)]
    (when (seq missing-keys)
      (throw (ex-info (str "Missing keys: " (vec (sort missing-keys)))
                      {:missing-keys missing-keys
                       :res res})))
    res))
