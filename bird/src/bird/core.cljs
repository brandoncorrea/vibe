(ns bird.core
  "Entry point: app atom, frame loop, input handlers."
  (:require [bird.config :as cfg]
            [bird.state :as state]
            [bird.game :as game]
            [bird.render :as render]))

(defonce app (atom (state/fresh-state)))

(defn frame [ctx]
  (let [s (swap! app game/step)]
    (render/render! ctx s))
  (js/requestAnimationFrame #(frame ctx)))

(defn- flap-or-restart! []
  (swap! app
         (fn [s]
           (case (:phase s)
             :game-over (if (>= (:tick s) 30) (state/restart s) s)
             (game/flap s)))))

(defn on-keydown [e]
  (when (#{"Space" "ArrowUp" "KeyW"} (.-code e))
    (.preventDefault e)
    (flap-or-restart!)))

(defn on-pointerdown [e]
  (.preventDefault e)
  (flap-or-restart!))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx    (.getContext canvas "2d")]
    (set! (.-width canvas) cfg/world-w)
    (set! (.-height canvas) cfg/world-h)
    (.addEventListener js/window "keydown" on-keydown)
    (.addEventListener canvas "pointerdown" on-pointerdown)
    (.addEventListener canvas "touchstart"
                       (fn [e] (.preventDefault e))
                       #js {:passive false})
    (js/requestAnimationFrame #(frame ctx))))
