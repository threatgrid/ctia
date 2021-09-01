(ns ctia.frontend.sample-chart
  (:require
   ["react" :as React]
   ["recharts" :as charts]
   [ajax.core :as ajax]
   [cljs-bean.core :refer [->clj ->js]]
   [day8.re-frame.http-fx]
   [re-frame.core :refer [dispatch
                          reg-event-fx
                          reg-event-db
                          reg-sub
                          subscribe]]
   [reagent.core :as reagent]))

(defn- random-color [x]
  (str "#"
       (-> 16777215
           (* (js/Math.random))
           (js/Math.floor)
           (.toString 16)
           (subs 0 4)
           #_(str "DF"))))

(def BarChart (reagent/adapt-react-class charts/BarChart))
(def CartesianGrid (reagent/adapt-react-class charts/CartesianGrid))
(def XAxis (reagent/adapt-react-class charts/XAxis))
(def YAxis (reagent/adapt-react-class charts/YAxis))
(def Tooltip (reagent/adapt-react-class charts/Tooltip))
(def Legend (reagent/adapt-react-class charts/Legend))
(def Bar (reagent/adapt-react-class charts/Bar))
(def Cell (reagent/adapt-react-class charts/Cell))

(defn customized-axis-tick [props]
  (let [{:keys [x y payload]} (->clj props)]
    (reagent/as-element
     [:g {:transform (str "translate(" (- x 12) "," y ")")}
      [:text
       {:x           0
        :y           0
        :dy          16
        :textAnchor  "end"
        :transform   "rotate(-55)"
        :font-size   "8pt"
        :font-family "Helvetica"
        :font-weight 200}
       (:value payload)]])))

(reg-event-fx
 ::fetch-data
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri "top5-incident-histogram.json"
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::process-data]}
    :db db}))

(reg-event-db
 ::process-data
 (fn [db [_ result]]
   (assoc db ::data result)))

(reg-sub ::data (fn [db _] (get db ::data)))

(reg-sub ::current-data-vector #(get % ::current-data-vector))

(defn pick-data
  "Digs up data from a nested map (with vectors).
  Path elements either keywords/strings, or maps where k/v pais is present
  somewhere in the data. Note that regardless of k/v pair uniqueness within the
  same level, the function always picks the first matching element.

  Examples:
  (def data {:foo {:bar {:zaps [{:key 42, :pos 1}{:key 43, :pos 2}]}}})

  (pick-data data [:foo :bar]           => {:zaps [{:key 42, :pos 1}{:key 43, :pos 2}]})
  (pick-data data [:foo :bar {:key 43}] => {:key 43, :pos 2})"
  [data path]
  (reduce
   (fn [acc n]
     (let [cur (if (and (map? n)
                        (sequential? acc))
                 (some->> acc
                          (filter #(= (second n) (get % (first n))))
                          first)
                 (get acc n))]
       cur))
   data
   path))

(reg-sub
 ::chart-data
 :<- [::data]
 :<- [::current-data-vector]
 (fn [[data {:keys [outer inner]}]]
   (let [raw (->> (pick-data data outer)
                  (map (fn [{:keys [key] :as m}]
                         (let [nested (->> (get-in m inner)
                                           (map (fn [{:keys [key doc_count]}]
                                                  (hash-map key doc_count)))
                                           (apply merge))]
                           (assoc nested :name key)))))]
     raw)))

(declare data-vectors)

(reg-event-db
 ::reset-current-data-vector
 (fn [db [_ key params]]
   (let [{:keys [title outer] :as dv} (first (filter #(-> % :key (= key)) data-vectors))
         title (if (fn? title) (title params) title)
         outer (if (fn? outer) (outer params) outer)
         dv (merge dv {:title title, :outer outer})]
     (assoc db ::current-data-vector dv))))

(reg-sub
 ::title
 :<- [::current-data-vector]
 #(get % :title))

(reg-sub
 ::ui-component
 :<- [::current-data-vector]
 (fn [cdw]
   (get cdw :ui-component)))

(reg-event-fx
 ::next-data-vector
 (fn [{:keys [db]} [_ data-vector-k params]]
   (let [nxt-k (->> data-vectors
                    (drop-while #(-> % :key (not= data-vector-k)))
                    rest first :key)]
     (when nxt-k
       {:dispatch [::reset-current-data-vector nxt-k (:name params)]}))))

(drop-last [:foo])

(reg-event-fx
 ::prev-data-vector
 (fn [{:keys [db]} [_ data-vector-k params]]
   (let [prev-k (as-> data-vectors $
                   (take-while #(-> % :key (not= data-vector-k)) $)
                   (if (< 1 (count $))
                     (drop-last $)
                     (last $))
                   (:key $))]
     (when prev-k
       {:dispatch [::reset-current-data-vector prev-k (:name params)]}))))

(defn bar-chart []
  (let [chart-data @(subscribe [::chart-data])
        current-data-vector @(subscribe [::current-data-vector])
        ;; chart-data-vector @(subscribe [::current-data-vector]) ;; chart-type
        ;; @(subscribe [::chart-type]) ;; title @(subscribe [::title])
        ;; {:keys [x-axis-key y-axis-key]} @(subscribe [::axis-keys])
        all-keys (disj (->> chart-data
                            (mapcat keys)
                            set)
                       :name)]
    [:div
     [BarChart
      {:width 1000
       :height 700
       :margin {:top 20
                :right 20
                :left 20
                :bottom 20}
       :data chart-data}
      [CartesianGrid {:strokeDasharray "2 2"
                      :stroke "#dcdcdc"}]
      [XAxis {:dataKey :name
              :interval 0
              :tick customized-axis-tick}]
      [YAxis {:scale :log
              :domain [1 "dataMax + 500"]
              :allowDataOverflow true
              ;; :tick {:font-family "Helvetica"}
              :tickCount 20
              :padding {:top 50 :bottom 5}}]
      ;; [Tooltip]
      [Legend {:layout :vertical
               :align :right
               :verticalAlign :top
               :width 200}]
      (for [k all-keys]
        [Bar {:dataKey k
              :key k
              :label true
              :name k
              :stackId "a"
              :minPointSize 20
              :fill (random-color k)
              :on-click #(dispatch [::next-data-vector
                                    (:key current-data-vector)
                                    (-> % ->clj (select-keys [:name]))])}])]]))

(rest (drop-while
  #(-> % :key (not= :root))
  data-vectors))

(def data-vectors
  [{:key          :root
    :title        "By Org and Event Source"
    :outer        [:aggregations :org :buckets]
    :inner        [:source :buckets]
    :ui-component bar-chart}
   {:key          :org
    :title        (fn [k] (str "By event type for Org: " k))
    :outer        (fn [k] [:aggregations :org :buckets
                           {:key k} :source :buckets])
    :inner        [:titles :buckets]
    :ui-component bar-chart}])

(defn root []
  (dispatch [::fetch-data])
  (dispatch [::reset-current-data-vector :root])
  (fn []
    (let [title @(subscribe [::title])
          ui-component @(subscribe [::ui-component])
          current-data-vector @(subscribe [::current-data-vector])]
      [:div
       [:h1 "CTIA Incidents"]
       [:div.title-container
        [:button.back-btn
         {:on-click #(dispatch [::prev-data-vector (:key current-data-vector)])} "<"]
        [:h2 title]]
       (when ui-component
         [ui-component])])))


(comment
  (require 're-frame.db)
  (def all-data
    (::data @re-frame.db/app-db))

  (pick-data all-data [:aggregations :org :buckets])
  (pick-data all-data
             [:aggregations :org :buckets
              {:key "93f3924d-d831-41af-8149-732ac64434a6"}
              :source]))
