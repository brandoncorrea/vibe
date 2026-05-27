(ns chart.scale)

(defn format-value
  "Render a value in billions as a short string (e.g. 8500 -> \"8.5 T\")."
  [v]
  (cond
    (zero? v)   "0"
    (>= v 1000) (str (.toFixed (/ v 1000) (if (>= v 10000) 0 1)) " T")
    :else       (str v " B")))

(defn y-pos
  "Map a value to a y-coordinate inside a plot area.

   `scale-kind` is `:linear` or `:log`. `vmax` is the largest value in the series.
   `top` and `inner-h` define the plot rectangle in pixels."
  [scale-kind {:keys [vmax top inner-h]} v]
  (let [bottom (+ top inner-h)]
    (case scale-kind
      :log    (let [lo (Math/log10 10)
                    hi (Math/log10 (* vmax 1.2))
                    v* (max v 1)]
                (- bottom (* (/ (- (Math/log10 v*) lo) (- hi lo)) inner-h)))
      :linear (- bottom (* (/ v (* vmax 1.1)) inner-h)))))

(defn ticks-for [scale-kind]
  (case scale-kind
    :log    [10 100 1000 10000]
    :linear [0 2000 4000 6000 8000]))
