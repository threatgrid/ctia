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

;; cyclic deps prevent moving these schemas to ctia.schemas.core
(s/defschema AnyServiceGraphNode
  "Describes the vals of the return values of app/service-graph"
  {(s/pred simple-keyword?) (s/pred ifn?)})

(s/defschema AnyServiceGraph
  "Describes all return values of app/service-graph"
  {(s/pred simple-keyword?) AnyServiceGraphNode})

(s/defn service-subgraph :- AnyServiceGraph
  "Returns a subgraph of a Trapperkeeper service graph. If
  any levels are missing, throws an exception.
  
  (service-subgraph
    {:ConfigService {:get-in-config <...>
                     :get-config <...>}
     :FooService {:f1 <...>
                  :f2 <...>}
     :BarService {:b1 <...>}}
    {:ConfigService #{:get-config}
     :FooService #{:f2 :f3}})
  ;=> {:ConfigService {:get-config <...>}
  ;    :FooService {:f2 <...>}}
  "
  [graph :- AnyServiceGraph
   selectors :- {(s/pred simple-keyword?)
                 #{(s/pred simple-keyword?)}}]
  {:pre [(map? graph)]}
  (persistent!
    (reduce (fn [out [service-kw fn-kws]]
              (assert (keyword? service-kw)
                      (pr-str service-kw))
              (assert (set? fn-kws))
              (let [service-fns (some-> (get graph service-kw)
                                        (select-keys fn-kws))]
                (when (not= (count service-fns)
                            (count fn-kws))
                  (throw (ex-info (str "Missing service functions for "
                                       service-kw ": "
                                       (set/difference
                                         (set fn-kws)
                                         (set (keys service-fns))))
                                  {})))
                (cond-> out
                  service-fns
                  (assoc! service-kw service-fns))))
            (transient {})
            selectors)))

(s/defn service-subgraph-from-schema :- AnyServiceGraph
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
  [graph :- AnyServiceGraph
   schema :- s/Schema]
  {:pre [(map? schema)]}
  (service-subgraph
    graph
    (into {}
          (comp (filter (comp s/specific-key? key))
                (map (fn [[service-kw service-fns]]
                       {:pre [(map? service-fns)]}
                       [(s/explicit-schema-key service-kw)
                        (->> service-fns
                             keys
                             (filter s/specific-key?)
                             (map s/explicit-schema-key)
                             set)])))
          schema)))

(s/defn service-subschema :- s/Schema
  "Given a schema shaped like a Trapperkeeper service graph, selects
  the specified services and their optionality from graph. Any missing levels
  in graph will throw an error.
  
  (service-subschema
    {:ConfigService {:get-in-config (s/=> ...)
                     (s/optional-key :get-config) (s/=> ...)}
     (s/optional-key :FooService) {:f1 (s/=> ...)
                                   :f2 (s/=> ...)}
     :BarService {:b1 (s/=> ...)}}
    {:ConfigService #{:get-config}
     :FooService #{:f2 :f3}
     :MissingService #{:m1})
  ;=> {:ConfigService {(s/optional-keys :get-config) (s/=> ...)}
  ;    (s/optional-key :FooService) {:f2 (s/=> ...)}}
  "
  [graph :- s/Schema
   selectors :- {(s/pred simple-keyword?)
                 #{(s/pred simple-keyword?)}}]
  {:pre [(map? graph)]}
  (persistent!
    (reduce (fn [out [service-kw fn-kws]]
              (assert (keyword? service-kw)
                      (pr-str service-kw))
              (assert (set? fn-kws))
              (let [service-fns (some-> (st/get-in graph [service-kw])
                                        (st/select-keys fn-kws))]
                (when (not= (count service-fns)
                            (count fn-kws))
                  (throw (ex-info (str "Missing service functions for "
                                       service-kw ": "
                                       (set/difference
                                         (set fn-kws)
                                         (->> service-fns keys (map s/explicit-schema-key))))
                                  {})))
                (cond-> out
                  service-fns
                  (assoc! service-kw service-fns))))
            (transient {})
            selectors)))

;; TODO behavioral unit test
(s/defn open-service-schema :- s/Schema
  "Given a schema shaped like a Trapperkeeper service graph, 
  st/merge's AnyServiceGraph to the first layer
  and AnyServiceGraphNode to the second layers.

  (open-service-schema
    {:ConfigService {:get-in-config (s/=> ...)}
     :FooService {:f1 (s/=> ...)}})
  ;=> (st/merge {:ConfigService (st/merge {:get-in-config (s/=> ...)}
  ;                                       AnyServiceGraphNode)
  ;              :FooService (st/merge {:f1 (s/=> ...)}
  ;                                    AnyServiceGraphNode)}
  ;             AnyServiceGraph)
  "
  [s :- s/Schema]
  {:pre [(map? s)]}
  (-> (into {}
            (map (fn [[k v]]
                   {:pre [(map? v)]}
                   [k (st/merge v AnyServiceGraphNode)]))
            s)
      (st/merge AnyServiceGraph)))

(defn select-all-keys
  "Like st/select-keys but throws an exception if any keys are missing."
  [schema ks]
  (let [res (st/select-keys schema ks)
        missing-keys (into #{}
                           ;; Note: st/key-in-schema is private but would be more direct
                           (remove #(st/get-in res [%]))
                           ks)]
    (when (seq missing-keys)
      (throw (ex-info (str "Missing keys: " (vec (sort missing-keys)))
                      {:missing-keys missing-keys
                       :res res})))
    res))
