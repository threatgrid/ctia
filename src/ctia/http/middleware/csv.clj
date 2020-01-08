(ns ctia.http.middleware.csv
  (:require
   [clojure.string :as string]
   [ring.util.http-response :refer [ok]]))

(defn ^:private field-protect
  [txt]
  (str "\""
       (-> (cond (string? txt) txt
                 (keyword? txt) (name txt)
                 :else (pr-str txt))
           (string/replace #"\"" "'")
           (string/replace #"\n" " "))
       "\""))

(defn- deep-flatten-map-as-couples
  [prefix m]
  (apply concat
         (for [[k v] m]
           (let [k-str (if (keyword? k) (name k) (str k))
                 new-pref (if (empty? prefix)
                            k-str
                            (str (name prefix) " " k-str))]
             (cond
               (map? v) (deep-flatten-map-as-couples new-pref v)
               (and (coll? v)
                    (first v)
                    (map? (first v)))
               (apply concat
                      (map-indexed #(deep-flatten-map-as-couples
                                     (if (> %1 0)
                                       (str new-pref "-" %1)
                                       new-pref)
                                     %2)
                                   v))
               :else [[(name new-pref)
                       (if (string? v) v (pr-str v))]])))))

(defn deep-flatten-map
  ([m] (deep-flatten-map "" m))
  ([prefix m]
   (into {} (deep-flatten-map-as-couples prefix m))))


(defn to-csv
  "Take a list of hash-map and output a CSV string
  Take it wasn't optimized and I might consume a lot of memory."
  [ms]
  (let [flattened-ms (map deep-flatten-map ms)
        ks (keys (apply merge flattened-ms))]
    (string/join "\n"
                 (cons (string/join "," (map #(field-protect (name %)) ks))
                       (for [m flattened-ms]
                         (string/join "," (map #(field-protect (get m % "")) ks)))))))

(defn csv-http-headers [filename]
  {"Content-Type" "text/csv; charset=utf8"
   "Content-Disposition" (str "attachment;filename=" filename)})

(def csv-mime-type "text/csv")

(defn csv? [mime-type]
  (= csv-mime-type mime-type))

(defn- add-file-extension
  "Add the csv file extension if it does not exist."
  [string]
  (if (.endsWith string ".csv")
    string
    (str string ".csv")))

(defn- uri->filename
  "Uses the last element of the path to build the filename"
  [uri]
  (-> uri
      (string/split #"/")
      last
      add-file-extension))

(defn csv
  "Builds a HTTP response from the flat api result."
  [result uri]
  (let [csv-output (to-csv result)
        response (into (ok csv-output)
                       {:compojure.api.meta/serializable? false})]
    (update-in response [:headers]
               #(into % (csv-http-headers
                         (uri->filename uri))))))

(defn wrap-csv
  [handler]
  (fn [{:keys [uri]
        :as request}]
    (let [csv? (= (get-in request [:headers "accept"]) "text/csv")
          {:keys [csv-render-fn
                  body]
           :as response} (handler request)]
      (if csv?
        (cond-> body
          csv-render-fn (csv-render-fn)
          true (csv uri))
        response))))
