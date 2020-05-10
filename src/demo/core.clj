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

(def conn
  {:dbtype "mysql"
   :dbname "demo"
   :host "localhost"
   :port 3306
   :user "demo"
   :password "123456"})

(defn create-post
  [{:keys [poster title content]}]
  (j/insert! conn :posts
    {:poster poster
     :content content}))

(defn find-posts
  [{:keys [created]}]
  (j/query conn ["select * from demo.posts where created > ? order by created desc limit 7" created]))

(defonce clients (atom #{}))

(defn notify-new-posts [created]
  (doseq [cli @clients]
    (httpkit/send! cli (cheshire.core/generate-string {:created created}))))

(def page-for-login
  (h/html
    [:form {:method "POST" :action "/login"}
     [:input {:autofocus "autofocus" :name "nick" :placeholder "Input your nick!"}]
     [:submit {:value "Login"}]]))

(defn index [{:keys [session]}]
  (if-let [nick (:nick session)]
    (resp/resource-response "public/index.html")
    (-> (resp/response page-for-login)
      (resp/content-type "text/html"))))

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

(defn ws-connect [req]
  (httpkit/with-channel req ch
    (httpkit/on-close ch (fn [status] (swap! clients disj ch)))
    (swap! clients conj ch)))

(defroutes route
  (GET "/" req index)
  (GET "/post" req query-post)
  (GET "/ws" req ws-connect)
  (POST "/login" req login)
  (POST "/post" req make-post)
  (route/resources "/" {:root "public"})
  (route/not-found "Not found!"))

(def app
  (-> route
    (wrap-keyword-params)
    (wrap-params)
    (wrap-session)
    (wrap-json-response)
    (wrap-json-params)))

(defonce server
  (httpkit/run-server (var app) {:port 3000}))
