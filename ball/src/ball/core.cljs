(ns ball.core
  "Entry point: app atom, frame loop, input handlers."
  (:require [ball.state :as state]
            [ball.game :as game]
            [ball.render :as render]))

(defonce app (atom (state/fresh-state 800 600)))

(defn resize! [canvas]
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (swap! app assoc :w w :h h)))

(defn frame [ctx]
  (let [s (swap! app game/step)]
    (render/render! ctx s))
  (js/requestAnimationFrame #(frame ctx)))

(def key-map
  {"ArrowLeft"  :left   "KeyA" :left
   "ArrowRight" :right  "KeyD" :right
   "ArrowUp"    :jump   "KeyW" :jump   "Space" :jump})

(defn on-keydown [e]
  (when-let [k (key-map (.-code e))]
    (.preventDefault e)
    (swap! app update :keys conj k)))

(defn on-keyup [e]
  (when-let [k (key-map (.-code e))]
    (swap! app update :keys disj k)))

(defn on-mousedown [e]
  (let [x (.-clientX e) y (.-clientY e)]
    (swap! app
           (fn [{:keys [phase w h] :as s}]
             (case phase
               (:game-over :win) (state/fresh-state w h)
               :playing (assoc s :aim {:sx x :sy y :ex x :ey y}))))))

(defn on-mousemove [e]
  (when (:aim @app)
    (swap! app update :aim assoc
           :ex (.-clientX e)
           :ey (.-clientY e))))

(defn on-mouseup [_e]
  (when (:aim @app)
    (swap! app game/fire)))

(defn init []
  (let [canvas (.getElementById js/document "stage")
        ctx (.getContext canvas "2d")]
    (resize! canvas)
    (swap! app (fn [{:keys [w h]}] (state/fresh-state w h)))
    (.addEventListener js/window "resize" #(resize! canvas))
    (.addEventListener js/window "keydown" on-keydown)
    (.addEventListener js/window "keyup" on-keyup)
    (.addEventListener canvas "mousedown" on-mousedown)
    (.addEventListener js/window "mousemove" on-mousemove)
    (.addEventListener js/window "mouseup" on-mouseup)
    (js/requestAnimationFrame #(frame ctx))))
