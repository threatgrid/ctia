(ns ctia.stores.es.query
  (:require
   [clojure.string :as str]
   [ctia.domain.access-control :as ac]
   [ctia.schemas.search-agg :refer [FullTextQuery]]
   [ctia.stores.es.schemas :refer [ESConnState]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defn find-restriction-query-part
  [{:keys [login groups]} get-in-config]
  ;; TODO do we really want to lower-case here?
  (let [login (str/lower-case login)
        groups (map str/lower-case groups)]
    {:bool
     {:should
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

(defn- unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"
  [time-str]
  [{:range
    {"valid_time.start_time" {"lte" time-str}}}
   {:range
    {"valid_time.end_time" {"gt" time-str}}}])

(defn active-judgements-by-observable-query
  "a filtered query to get judgements for the specified
  observable, where valid time is in now range"
  [{:keys [value type]} time-str]
  (concat
   (unexpired-time-range time-str)
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
     (mapcat (fn [[k v]]
               (searchable-fields-map-impl
                 (cond-> path
                   (not= k :properties) (conj k))
                 v))
             m)
     (keep (fn [[field {:keys [type]}]]
             (when (= type "text")
               [path field]))
           (:fields m)))))

(s/defn searchable-fields-map :- {s/Str s/Str}
  "Walks through entity's mapping properties and finds all fields with secondary searchable token.
Returns a map where key is path to a field, and value - path to the nested text token."
  [properties :- (s/maybe {s/Keyword s/Any})]
  (into {} (map (fn [[path field]]
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
         {{:keys [flag-value]} :FeaturesService} :services} es-conn-state]
    (or (seq fields)
        (when (= "true" (flag-value :enforce-search-fields))
          (mapv name searchable-fields)))))

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
      (when-some [fields (seq fields)]
        (let [mapping (searchable-fields-map (some-> es-conn-state :config :mappings first second :properties))]
          (mapv (comp #(get mapping % %) name)
                fields))))))

(s/defn refine-full-text-query-parts :- [{s/Keyword ESQFullTextQuery}]
  [es-conn-state :- ESConnStateProps
   full-text-terms :- [FullTextQuery]]
  (let [{{:keys [default_operator]} :props} es-conn-state
        term->es-query-part (fn [{:keys [query_mode fields] :as text-query}]
                              (let [fields* (->> fields
                                                 (enforce-search-fields es-conn-state)
                                                 (rename-search-fields es-conn-state))]
                                {(or query_mode :query_string)
                                 (-> text-query
                                     (dissoc :query_mode)
                                     (cond->
                                       (and default_operator (not= query_mode :multi_match))
                                       (assoc :default_operator default_operator)

                                       fields* (assoc :fields fields*)))}))]
    (mapv term->es-query-part full-text-terms)))
