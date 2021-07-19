(ns ctia.frontend.app
  (:require [reagent.dom :as re-dom]))

(defn ^:export init []
  (println "init"))

(defn root-view []
  [:div
   [:h1 "I am Groot"]])

(defn ^:dev/after-load render! []
  (re-dom/render
   [root-view]
   (js/document.getElementById "app")))
