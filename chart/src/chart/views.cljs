(ns chart.views
  (:require [reagent.core :as r]
            [chart.data :as data]
            [chart.scale :as scale]))

(def ^:private W 960)
(def ^:private H 520)
(def ^:private margin {:top 30 :right 24 :bottom 70 :left 78})
(def ^:private inner-w (- W (:left margin) (:right margin)))
(def ^:private inner-h (- H (:top margin) (:bottom margin)))

(def ^:private vmax
  (apply max (map :value data/photographs-by-period)))

(def ^:private plot
  {:vmax vmax :top (:top margin) :inner-h inner-h})

(defonce ^:private scale-kind (r/atom :linear))

(defn- grid-layer [s]
  [:g.grid
   (for [t (scale/ticks-for s)
         :let [y (scale/y-pos s plot (max t 1))]]
     ^{:key t}
     [:g
      [:line {:x1 (:left margin) :x2 (+ (:left margin) inner-w)
              :y1 y :y2 y}]
      [:text {:x (- (:left margin) 10) :y (+ y 4)
              :text-anchor "end" :class "label"
              :fill "var(--muted)"}
       (scale/format-value t)]])])

(defn- bar [s i {:keys [label value]}]
  (let [band-w (/ inner-w (count data/photographs-by-period))
        bar-w  (* band-w 0.66)
        bottom (+ (:top margin) inner-h)
        x      (+ (:left margin) (* i band-w) (/ (- band-w bar-w) 2))
        y-top  (scale/y-pos s plot value)
        h      (- bottom y-top)]
    [:g
     [:rect.bar {:x x :y y-top :width bar-w :height h :rx 3}
      [:title (str label ": " (scale/format-value value))]]
     [:text.label {:x (+ x (/ bar-w 2)) :y (- y-top 6)}
      (scale/format-value value)]
     [:text.label {:x (+ x (/ bar-w 2)) :y (+ bottom 18)
                   :fill "var(--muted)"}
      label]]))

(defn- bars-layer [s]
  (into [:g]
        (map-indexed
          (fn [i d] ^{:key (:label d)} [bar s i d])
          data/photographs-by-period)))

(defn- y-axis-label []
  (let [mid (+ (:top margin) (/ inner-h 2))]
    [:text {:x 16 :y mid
            :transform (str "rotate(-90 16 " mid ")")
            :text-anchor "middle"
            :fill "var(--muted)" :font-size 12}
     "Photographs (B = billion, T = trillion)"]))

(defn- chart-svg []
  (let [s @scale-kind]
    [:svg {:viewBox (str "0 0 " W " " H)
           :preserveAspectRatio "xMidYMid meet"
           :aria-label "Bar chart of estimated photographs taken per 5-year interval"}
     [grid-layer s]
     [y-axis-label]
     [bars-layer s]
     [:line {:x1 (:left margin) :x2 (+ (:left margin) inner-w)
             :y1 (+ (:top margin) inner-h)
             :y2 (+ (:top margin) inner-h)
             :stroke "var(--grid)"}]]))

(defn- scale-toggle []
  [:div.toggle {:role "tablist"}
   (for [[k label] [[:linear "Linear"] [:log "Log"]]]
     ^{:key k}
     [:button {:class (when (= @scale-kind k) "on")
               :on-click #(reset! scale-kind k)}
      label])])

(defn- methodology []
  [:p.source
   [:b "Methodology."]
   " Pre-2000 values follow film-industry consumption (Kodak / Fuji film roll shipments × ~24 exposures). "
   "2000-onward values are synthesized from industry estimates published by InfoTrends, Mylio Photos, and "
   "KeyPoint Intelligence, which place 2023 alone at roughly 1.6 trillion photos. Numbers are rounded "
   "order-of-magnitude estimates, not measurements."])

(defn app []
  [:div.wrap
   [:h1 "Estimated photographs taken worldwide"]
   [:p.sub "Aggregate per 5-year interval, 1976 – 2025"]
   [scale-toggle]
   [chart-svg]
   [methodology]])
