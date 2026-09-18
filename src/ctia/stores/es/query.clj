(ns ctia.stores.es.query
  (:require
   [clojure.string :as str]
   [ctia.domain.access-control :as ac]
   [ctia.schemas.search-agg :as search-schemas
    :refer [FullTextQuery]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defn- normalize-ident
  ;; TODO do we really want to discard case on that?
  [{:keys [login groups]}]
  {:login (str/lower-case login)
   :groups (map str/lower-case groups)})

(defn- write-restriction-should-clauses
  "The should-clauses shared by the read and write access-control filters.
   These mirror the disjuncts of `ctia.domain.access-control/allow-write?`:
   document owner (TLP-independent), `authorized_users`, `authorized_groups`,
   and same-group records at TLP amber or below.

   The trailing TLP-red owner clause is redundant: `allow-write?` has no red
   branch, and its owner rule matches at any TLP, so the owner should-clause
   above already covers same-group owner records at TLP red. It is kept
   unchanged only so `find-restriction-query-part` (read) emits the exact same
   query body as before these clauses were factored out — the read filter must
   stay byte-identical (see XFV-120)."
  [login groups]
  [;; Document Owner
   {:bool {:filter [{:term {"owner" login}}
                    {:terms {"groups" groups}}]}}

   ;; or if user is listed in authorized_users or authorized_groups field
   {:term {"authorized_users" login}}
   {:terms {"authorized_groups" groups}}

   ;; CTIM records with TLP equal or below amber that are owned by org BAR
   {:bool {:must [{:terms {"tlp" (conj ac/public-tlps "amber")}}
                  {:terms {"groups" groups}}]}}

   ;; CTIM records with TLP red owned by user FOO. Redundant with the owner
   ;; clause above (which is TLP-independent, matching owner+group at any TLP);
   ;; retained only to keep the read filter byte-identical to its pre-refactor
   ;; form. See the fn docstring and XFV-120.
   {:bool {:must [{:term {"tlp" "red"}}
                  {:term {"owner" login}}
                  {:terms {"groups" groups}}]}}])

(defn find-restriction-query-part
  "Access-control filter for read operations. In addition to the write
   disjuncts, when `ctia.access-control.max-record-visibility` is `everyone`
   it also matches any TLP white/green document regardless of owner/groups."
  [ident get-in-config]
  (let [{:keys [login groups]} (normalize-ident ident)]
    {:bool
     {:minimum_should_match 1
      :should
      (cond->> (write-restriction-should-clauses login groups)
        ;; Any Green/White TLP if max-visibility is set to `everyone`
        (ac/max-record-visibility-everyone? get-in-config)
        (cons {:terms {"tlp" ac/public-tlps}}))}}))

(defn find-write-restriction-query-part
  "Access-control filter for write/delete operations. Mirrors the disjuncts of
   `ctia.domain.access-control/allow-write?`. Unlike `find-restriction-query-part`
   it never adds the `max-record-visibility=everyone` public-TLP clause, so a
   caller cannot reach another group's TLP white/green records by blanket
   visibility alone. It can still match a foreign group's record when that
   record explicitly grants the caller via `authorized_users`/`authorized_groups`
   — that is intended, since `allow-write?` grants write on the same basis.
   See XFV-120."
  [ident]
  (let [{:keys [login groups]} (normalize-ident ident)]
    {:bool
     {:minimum_should_match 1
      :should (write-restriction-should-clauses login groups)}}))


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
