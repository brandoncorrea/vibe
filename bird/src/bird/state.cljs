(ns bird.state
  "Initial state shape."
  (:require [bird.config :as cfg]))

(defn fresh-state []
  {:phase  :ready
   :bird   {:x cfg/bird-x
            :y (/ cfg/world-h 2)
            :vy 0
            :flap-anim 0}
   :pipes  []
   :spawn-t 0
   :score   0
   :best    0
   :tick    0
   :flash   0})

(defn restart [s]
  (assoc (fresh-state) :best (:best s 0) :phase :ready))
