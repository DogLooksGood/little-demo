(ns demo.core
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [clojure.string :as str]
            [ring.util.response :as resp]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body wrap-json-params]]
            [clojure.java.jdbc :as j]
            [taoensso.timbre :as log]
            [hiccup.core :as h]
            [hiccup.page :as hpage]))

;;; 使用一个 Map 来表示一个数据库的连接
;;; 这只是最简单的情况，实际中我们会使用连接池
(def conn
  {:dbtype "mysql"
   :dbname "demo"
   :host "localhost"
   :port 3306
   :user "demo"
   :password "123456"})

;;; 两个 Model 层的函数，用来添加和查找数据
;;; 我们使用 [sql & args] 的形式来表达一个 PreparedStatement
(defn create-post
  [{:keys [poster title content]}]
  (j/insert! conn :posts
    {:poster poster
     :content content}))

(defn find-posts
  [{:keys [created]}]
  (j/query conn ["select * from demo.posts where created > ? order by created desc limit 7" created]))

;;; 我们用一个 Atom 来记录 WebSocket 连接的客户端
(defonce clients (atom #{}))

;;; 通过 WebSocket 通知前端有新的数据产生了
(defn notify-new-posts [created]
  (doseq [cli @clients]
    (httpkit/send! cli (cheshire.core/generate-string {:created created}))))

;;; 这个是 WebSocket 连接的初始化入口
(defn ws-connect [req]
  (httpkit/with-channel req ch
    (httpkit/on-close ch (fn [status] (swap! clients disj ch)))
    (swap! clients conj ch)))

;;; 一个用来 Login 的页面内容
;;; 后端和前端都使用相同的结构来表达 HTML
(def page-for-login
  (h/html
    [:form {:method "POST" :action "/login"}
     [:input {:autofocus "autofocus" :name "nick" :placeholder "Input your nick!"}]
     [:submit {:value "Login"}]]))

;;; 首页，如果有 Session 才能使用
;;; 否则会去 Login 页面
(defn index [{:keys [session]}]
  (if-let [nick (:nick session)]
    (resp/resource-response "public/index.html")
    (-> (resp/response page-for-login)
      (resp/content-type "text/html"))))

;;; 登录
(defn login
  [{:keys [params]}]
  (if-let [nick (:nick params)]
    (merge (resp/redirect "/")
      {:session {:nick nick}})
    (resp/redirect "/")))

(defn make-post [{:keys [session params] :as req}]
  (log/info session params)
  (if-let [nick (:nick session)]
    (let [{:keys [content]} params
          created (java.util.Date.)]
      (create-post {:poster nick
                    :content content
                    :created created})
      (notify-new-posts created)
      (resp/status 200))
    (resp/not-found "Need login!")))

(defn query-post [{:keys [params]}]
  (let [{:keys [created]} params
        created (if (str/blank? created) 0 created)
        posts (->> (find-posts {:created created})
                (into []))]
    (resp/response posts)))

;;; 路由规则
;;; Clojure 中有很多路由的库，他们有不同的语法
;;; 这里使用 Compojure 是一个比较传统的方案
(defroutes route
  (GET "/" req index)
  (GET "/post" req query-post)
  (GET "/ws" req ws-connect)
  (POST "/login" req login)
  (POST "/post" req make-post)
  (route/resources "/" {:root "public"})
  (route/not-found "Not found!"))

;;; 对路由应用一些中间件
(def app
  (-> route
    (wrap-keyword-params)
    (wrap-params)
    (wrap-session)
    (wrap-json-response)
    (wrap-json-params)))

;;; 单例的 Server
(defonce server
  (httpkit/run-server (var app) {:port 3000}))
