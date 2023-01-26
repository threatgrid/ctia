(ns ctia.stores.es.query
  (:require
   [clojure.string :as str]
   [ctia.domain.access-control :as ac]
   [ctia.schemas.search-agg :as search-schemas
    :refer [FullTextQuery]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defn find-restriction-query-part
  [{:keys [login groups]} get-in-config]
  ;; TODO do we really want to discard case on that?
  (let [login (str/lower-case login)
        groups (map str/lower-case groups)]
    {:bool
     {:minimum_should_match 1
      :should
      (cond->>
       [;; Document Owner
        {:bool {:filter [{:term {"owner" login}}
                         {:terms {"groups" groups}}]}}

           ;; or if user is listed in authorized_users or authorized_groups field
        {:term {"authorized_users" login}}
        {:terms {"authorized_groups" groups}}

           ;; CTIM records with TLP equal or below amber that are owned by org BAR
        {:bool {:must [{:terms {"tlp" (conj ac/public-tlps "amber")}}
                       {:terms {"groups" groups}}]}}

           ;; CTIM records with TLP red that is owned by user FOO
        {:bool {:must [{:term {"tlp" "red"}}
                       {:term {"owner" login}}
                       {:terms {"groups" groups}}]}}]

        ;; Any Green/White TLP if max-visibility is set to `everyone`
        (ac/max-record-visibility-everyone? get-in-config)
        (cons {:terms {"tlp" ac/public-tlps}}))}}))


(s/defn make-date-range-query :- search-schemas/RangeQuery
  [{:keys [from to date-field]
    :or {date-field :created}}]
  (let [date-range (cond-> {}
                     from (assoc :gte from)
                     to   (assoc :lt to))]
    (cond-> {}
      (seq date-range) (assoc date-field date-range))))

(defn- unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"
  [time-str]
  [{:range
    {"valid_time.start_time" {"lte" time-str}}}
   {:range
    {"valid_time.end_time" {"gt" time-str}}}])

(defn- time-opts-range-query
  [{:keys [date-range now-str]}]
  (if (seq date-range)
    [{:range (make-date-range-query date-range)}]
    (unexpired-time-range now-str)))

(defn active-judgements-by-observable-query
  "Query to fetch judgments by given observable
  and time options.

  `time-opts` can have the following otpional keys,
  * `:now-str` - filters using valid_time field
  * `:date-range`- filters using created field.
  `:now-str` is used when `:date-range` is
  not provided."
  [{:keys [value type]} time-opts]
  (concat
   (time-opts-range-query time-opts)
   [{:term {"observable.type" type}}
    {:term {"observable.value" value}}]))

(s/defschema ESQFullTextQuery
  (st/merge
   {:query s/Str}
   (st/optional-keys
    {:default_operator s/Str
     :fields [s/Str]})))

(defn- searchable-fields-map-impl
  ([m] (searchable-fields-map-impl [] m))
  ([path m]
   (if (and (map? m) (not= (:type m) "keyword"))
     (let [step-fn (fn [k]
                     (let [path (if (= k :properties) path (conj path k))]
                       (searchable-fields-map-impl path (m k))))]
       (mapcat step-fn (keys m)))
     (let [fields (reduce-kv
                   (fn [acc field {:keys [type]}]
                     (if (= type "text")
                       (conj acc field)
                       acc))
                   []
                   (:fields m))]
       (map #(conj [path] %) fields)))))

(s/defn searchable-fields-map :- {s/Str s/Str}
  "Walks through entity's mapping properties and finds all fields with secondary searchable token.
Returns a map where key is path to a field, and value - path to the nested text token."
  [properties :- (s/maybe {s/Keyword s/Any})]
  (into {}
        (map (fn [[path field]]
               (let [path (str/join "." (map name path))]
                 [path
                  (str/join "." [path (name field)])])))
        (searchable-fields-map-impl properties)))

(s/defschema ESConnStateProps
  (st/optional-keys
   {:config {s/Any s/Any}
    :props {s/Keyword s/Any}
    :index s/Any
    :conn s/Any
    :services s/Any
    :searchable-fields (s/maybe #{s/Keyword})}))

(s/defn enforce-search-fields :- [s/Str]
  [es-conn-state :- ESConnStateProps
   fields :- [s/Str]]
  (let [{:keys [searchable-fields]
         {{:keys [flag-value]} :FeaturesService} :services} es-conn-state
        searchable-fields (mapv name searchable-fields)]
    (if (and (empty? fields)
             (= "true" (flag-value :enforce-search-fields))
             (seq searchable-fields))
      searchable-fields
      fields)))

(s/defn rename-search-fields :- [s/Str]
  "Automatically translates keyword fields to use underlying text field.

   ES doesn't like when different types of tokens get used in the same query. To deal with
   that, we create a nested field of type 'text', see:
   `ctia.stores.es.mapping/searchable-token`. This should be opaque - caller shouldn't
   have to explicitly instruct API to direct query to the nested field."
  [es-conn-state :- ESConnStateProps
   fields :- [s/Any]]
  (let [{{{:keys [flag-value]} :FeaturesService} :services} es-conn-state]
    (when (= "true" (flag-value :translate-searchable-fields))
      (let [es-version (get-in es-conn-state [:props :version])
            mappings (some-> es-conn-state :config :mappings)
            properties (cond-> mappings
                         (= 5 es-version) (-> first second)
                         :always :properties)
            mapping (searchable-fields-map properties)]
        (when (seq fields)
          (mapv (comp #(get mapping % %) name) fields))))))

(s/defn refine-full-text-query-parts :- [{s/Keyword ESQFullTextQuery}]
  [es-conn-state :- ESConnStateProps
   full-text-terms :- [FullTextQuery]]
  (let [{{:keys [default_operator]} :props} es-conn-state
        term->es-query-part (fn [{:keys [query_mode fields] :as text-query}]
                              (let [fields* (->> fields
                                                 (enforce-search-fields es-conn-state)
                                                 (rename-search-fields es-conn-state))]
                                (hash-map
                                 (or query_mode :query_string)
                                 (-> text-query
                                     (dissoc :query_mode)
                                     (merge
                                      (when (and default_operator
                                                 (not= query_mode :multi_match))
                                        {:default_operator default_operator})
                                      (when fields*
                                        {:fields fields*}))))))]
    (mapv term->es-query-part full-text-terms)))
