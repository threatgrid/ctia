(ns ctia.stores.es.query
  (:require [clj-momo.lib.es.query :as q]
            [ctia.domain.access-control
             :refer [public-tlps]]
            [clojure.string :as str]))

(defn find-restriction-query-part
  [{:keys [login groups]}]
  ;; TODO do we really want to discard case on that?
  (let [login (str/lower-case login)
        groups (map str/lower-case groups)]
    {:bool
     {:should
      [{:terms {"tlp" public-tlps}}
       ;; Document Owner
       {:bool {:filter [{:term {"owner" login}}
                        {:terms {"groups" groups}}]}}

       ;; or if user is listed in authorized_users or authorized_groups field
       {:term {"authorized_users" login}}
       {:terms {"authorized_groups" groups}}

       ;; CTIM models with TLP amber that is owned by org BAR
       {:bool {:must [{:term {"tlp" "amber"}}
                      {:terms {"groups" groups}}]}}

       ;; CTIM models with TLP red that is owned by user FOO
       {:bool {:must [{:term {"tlp" "red"}}
                      {:term {"owner" login}}
                      {:terms {"groups" groups}}]}}]}}))

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

(defn nested-terms
  "make nested terms from a ctia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]
  we force all values to lowercase, since our indexing does the same for all terms."
  [filters]
  (vec (map (fn [[k v]]
              (q/terms (->> k
                            (map name)
                            (str/join "."))
                       (map str/lower-case
                            (if (coll? v) v [v]))))
            filters)))

;; TODO figure out better that part
(defn filter-map->terms-query
  "transforms a filter map to en ES terms query"
  ([filter-map]
   (filter-map->terms-query filter-map nil))
  ([filter-map query]

   (let [terms (map (fn [[k v]]
                      (let [t-key (if (sequential? k) k [k])]
                        [t-key v]))
                    filter-map)
         must-filters (nested-terms terms)]

     (cond
       ;; a filter map and a query
       (and filter-map query)
       {:bool
        {:filter (conj must-filters query)}}

       ;; only a filter map
       (and filter-map (nil? query))
       {:bool
        {:filter must-filters}}

       ;; a query without a filter map
       (and (empty? filter-map) query)
       {:bool
        {:filter query}}

       ;; if we neither have a filter map or a query
       :else
       {:bool
        {:match_all {}}}))))
