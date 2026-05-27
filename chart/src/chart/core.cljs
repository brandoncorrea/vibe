(ns chart.core
  (:require [reagent.dom :as rdom]
            [chart.views :as views]))

(defn- mount []
  (rdom/render [views/app]
               (.getElementById js/document "app")))

(defn ^:export init []
  (mount))

(defn ^:dev/after-load reload! []
  (mount))
