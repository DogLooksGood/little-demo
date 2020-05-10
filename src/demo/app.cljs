(ns demo.app
  (:require [cljs.core.async :as asyn :refer [go go-loop <! >! put! chan]]
            [reagent.core :as reagent]
            [reagent.dom :as rd]
            [cljs-http.client :as http]
            [taoensso.timbre :as log]))

(defonce state
  (reagent/atom
    {:posts []}))

(defn fetch-posts [created]
  (go
    (let [posts (:posts @state)
          created (or created (-> posts first :created))
          resp (<! (http/get "/post" {:query-params {:created created}}))
          posts (:body resp)]
      (when posts
        (swap! state update :posts
          (fn [old-posts]
            (->> []
              (into old-posts)
              (into posts))))))))

(defn make-post [content callback]
  (go
    (let [resp (<! (http/post "/post" {:json-params {:content content}}))]
      (log/info resp)
      (callback))))

(defn connect-ws []
  (let [ws (js/WebSocket. (str "ws://" (.-host js/location) "/ws"))]
    (set! (.-onmessage ws)
      (fn [e]
        (let [{:keys [created]} (js->clj (js/JSON.parse (.-data e)))]
          (fetch-posts created))))
    (set! (.-onclose ws)
      (fn []
        (js/setTimeout connect-ws 1000)))
    (set! (.-onerror ws)
      (fn []
        (.close ws)))
    ws))

(defonce ws (connect-ws))

(defn format-created [created]
  (.format (js/Intl.DateTimeFormat "zh-CN" #js {:dateStyle "full" :timeStyle "short"})
    (js/Date. created)))

(defn post-item [{:keys [id poster content created]}]
  [:div.pa3.ma3.br3.ba.b--silver.shadow-1
   [:div.flex.justify-between
    [:div.bold.sans poster]
    [:div.gray (format-created created)]]
   [:div.pa2 content]])

(defn on-input-confirm [e]
  (if (= (.-keyCode e) 13)
    (let [tgt (.-target e)
          val (.-value tgt)]
      (make-post val (fn [] (set! (.-value tgt) ""))))))

(defn input-panel []
  [:div.flex.w-100.bg-blue
   [:input.h3.ma3.ba.b--silver.br3.f3.flex-auto.ph3
    {:auto-focus true
     :placeholder "Say something?"
     :on-key-down on-input-confirm}]])

(defn posts-list []
  (fetch-posts nil)
  (fn []
    (let [items (:posts @state)]
      [:div.pa3.flex.flex-column
       (for [post items]
         ^{:key (:id post)}
         [post-item post])])))

(defn root []
  [:div.vh-100
   [:div.flex.flex-column.justify-center.items-center.bg-dark-blue.sans-serif
    [:div.ma3.white "POSTERS"]
    [input-panel]]
   [posts-list]])

(defn init []
  (rd/render [root]
    (.getElementById js/document "app")))

(defn after-load []
  (init))
