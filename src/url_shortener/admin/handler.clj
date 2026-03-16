(ns url-shortener.admin.handler
  (:require
    [hiccup2.core :as h]
    [clojure.core.async :as a :refer [chan sliding-buffer <!! sub unsub close! thread]]
    [hiccup.page :refer [html5]]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
    [url-shortener.admin.render :as render]))

(defn- admin-page []
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Admin Dashboard"]
     [:link {:rel "stylesheet" :href "/css/tokens.css"}]
     [:link {:rel "stylesheet" :href "/css/layout.css"}]
     [:link {:rel "stylesheet" :href "/css/admin.css"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div.center
      [:div.sidebar
       [:nav.sidebar__aside.stack
        [:div.nav__title "Admin"]
        [:a.nav__link {:href "#stats"}     "Overview"]
        [:a.nav__link {:href "#links"}     "Links"]
        [:a.nav__link {:href "#countries"} "Countries"]]
       [:main.sidebar__main.stack {:data-signals "{connected: false}" :data-init "@get('/admin/stream')"}
        [:div.cluster
         [:span.live-dot {:data-class "{active: $connected}"}]
         [:span {:style "font-size: var(--step--1); color: var(--color-muted)"}
          "Live"]]
        [:div.switcher {:id "stats"}
         [:div.box.stack
          [:span.stat__label "Total Links"]
          [:span.stat__value "--"]]
         [:div.box.stack
          [:span.stat__label "Total Clicks"]
          [:span.stat__value "--"]]
         [:div.box.stack
          [:span.stat__label "Unique Visitors"]
          [:span.stat__value "--"]]]
        [:div.grid {:id "links"}]
        [:div {:id "countries"}]]]]])) ; main, sidebar, center, body close + html5 + defn

(defn handle-admin-stream [pubsub request]
  (hk-gen/->sse-response request
    {hk-gen/on-open
     (fn [sse]
       (let [ch (chan (sliding-buffer 10))
             cleanup! (fn [e]
                        (log/debug "admin stream closed" (.getMessage e))
                        (unsub (:publication pubsub) :click ch)
                        (close! ch))]
         (sub (:publication pubsub) :click ch)
         (try
           (d*/patch-signals! sse "{\"connected\": true}")
           (d*/patch-elements! sse (render/render-stats))
           (d*/patch-elements! sse (render/render-links))
           (d*/patch-elements! sse (render/render-countries))
           (loop []
             (when-let [event (<!! ch)]
               (try
                 (d*/patch-elements! sse (render/render-stats))
                 (d*/patch-elements! sse (render/render-links))
                 (d*/patch-elements! sse (render/render-countries))
                 (catch Exception e (cleanup! e)))
               (recur)))
           (catch Exception e (cleanup! e)))))}))


(defn handle-admin [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (admin-page)})
