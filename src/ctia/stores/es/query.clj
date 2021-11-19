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
  ;; TODO do we really want to discard case on that?
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
  [prev m result]
  (reduce-kv
   (fn [res key val]
     (if (= "keyword" (:type val))
       (let [field-name (some->> val
                                 :fields
                                 (filter (fn [[_ v]] (= "text" (:type v))))
                                 ffirst
                                 name)
             path (conj prev (name key))]
         (if field-name
           (conj res path field-name)
           res))
       (if (map? val)
         (let [path (if (not= :properties key)
                      (conj prev (name key))
                      prev)]
           (searchable-fields-map-impl path val res))
         res)))
   result m))

(s/defn searchable-fields-map :- {s/Str s/Str}
  "Walks through entity's mapping properties and finds all fields with secondary searchable token.
Returns a map where key is path to a field, and value - path to the nested text token."
  [properties :- (s/maybe {s/Keyword s/Any})]
  (->> (searchable-fields-map-impl [] properties [])
       (partition 2)
       (map (fn [[p v]]
              (let [path (str/join "." p)]
                (hash-map path (str path "." v)))))
       (apply merge {})))

(s/defschema EntityProps
  (st/optional-keys
   {:default_operator s/Str}))

(s/defschema ESConnStateProps
  (st/optional-keys
   {:config {s/Any s/Any}
    :props EntityProps}))

(s/defn rename-search-fields :- (s/maybe {s/Keyword [s/Str]})
  "Automatically translates keyword fields to use underlying text field.

   ES doesn't like when different types of tokens get used in the same query. To deal with
   that, we create a nested field of type 'text', see:
   `ctia.stores.es.mapping/searchable-token`. This should be opaque - caller shouldn't
   have to explicitly instruct API to direct query to the nested field."
  [es-conn-state :- ESConnStateProps
   fields :- (s/maybe [s/Any])]
  (let [properties (some-> es-conn-state :config :mappings first second :properties)
        mapping (searchable-fields-map properties)]
    (when fields
      {:fields
       (mapv (comp #(get mapping % %) name) fields)})))

(s/defn refine-full-text-query-parts :- [{s/Keyword ESQFullTextQuery}]
  [es-conn-state :- ESConnStateProps
   full-text-terms :- [FullTextQuery]]
  (let [{{:keys [default_operator]} :props} es-conn-state
        term->es-query-part (fn [{:keys [query_mode fields] :as text-query}]
                              (hash-map
                               (or query_mode :query_string)
                               (-> text-query
                                   (dissoc :query_mode)
                                   (merge
                                    (when (and default_operator
                                               (not= query_mode :multi_match))
                                      {:default_operator default_operator})
                                    (rename-search-fields es-conn-state fields)))))]
    (mapv term->es-query-part full-text-terms)))
