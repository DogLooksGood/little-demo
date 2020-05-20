(ns demo.app
  (:require [cljs.core.async :as asyn :refer [go go-loop <! >! put! chan]]
            [reagent.core :as reagent]
            [reagent.dom :as rd]
            [cljs-http.client :as http]
            [taoensso.timbre :as log]))

;;; 前端状态管理
;;; 使用 Atom 来表达前端的状态
;;; 这里的 `reagent/atom` 和 Atom 的使用方式相同，区别在于如果其值发生改变，可以触发使用到该 atom 的 UI 的重绘。
(defonce state
  (reagent/atom
    {:posts []}))


;;; 前端 Transport
;;; 使用 cljs-http 库来发送网络请求，该库使用了 core.async 做为异步模型
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

;;; 连接 WebSocket
;;; 这里演示如何直接应用 JavaScript 中的函数和对象
;;; 这里的 WebSocket 只有接受消息的情况（服务器通知有新的数据产生）
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

;;; 这里的 defonce 确保页面在热更刷新的时候不会重连 WebSocket
(defonce ws (connect-ws))

;;; 演示如何用 JavaScript 内置的 API 来格式化一个时间
;;; 注意这里的 `#js`, ClojureScript 使用和 Clojure 一致的数据结构，如果需要 JavaScript 原生的结构，使用 `#js` 这个 Tag 来标注
(defn format-created [created]
  (.format (js/Intl.DateTimeFormat "zh-CN" #js {:dateStyle "full" :timeStyle "short"})
    (js/Date. created)))

;;; 以下是 UI 组件
;;; 这里使用了 tachyons 这个 CSS 库
(defn post-item [{:keys [id poster content created]}]
  [:div.pa3.ma3.br3.ba.b--silver.shadow-1
   [:div.flex.justify-between
    [:div.bold.sans poster]
    [:div.gray (format-created created)]]
   [:div.pa2 content]])

;;; ClojureScript 的函数就是 JavaScript 的函数。
;;; 这里提供一个事件的回调函数
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

;;; 页面初始化的逻辑
(defn init []
  (rd/render [root]
    (.getElementById js/document "app")))

;;; 页面热更新之后运行的函数
(defn after-load []
  (init))
