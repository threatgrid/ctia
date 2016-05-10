(ns ctia.http.routes.common
  (:require [clojure.string :as str]
            [clj-http.headers :refer [canonicalize]]
            [ring.util.http-status :refer [ok]]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]))

(s/defschema PagingParams
  {(s/optional-key :sort_by) (describe (s/enum :id) "Sort results on a field")
   (s/optional-key :sort_order) (describe (s/enum :asc :desc) "Sort direction")
   (s/optional-key :offset) (describe Long "Pagination Offset")
   (s/optional-key :limit) (describe Long "Pagination Limit")})

(defn map->paging-header-value [m]
  (str/join "&" (map (fn [[k v]]
                       (str (name k) "=" v)) m)))

(defn map->paging-headers
  "transform a map to a headers map
  {:total-hits 42}
  --> {'X-Total-Hits' '42'}"
  [headers]
  (reduce into {} (map (fn [[k v]]
                         {(->> k
                               name
                               (str "x-")
                               canonicalize)

                          (if (map? v)
                            (map->paging-header-value v)
                            (str v))}) headers)))

(defn paginated-ok
  "returns a 200 with the supplied response
   and its metas as headers"
  [{:keys [data paging]
    :or {data []
         paging {}}}]

  {:status ok
   :body data
   :headers (map->paging-headers paging)})
