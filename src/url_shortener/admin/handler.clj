(ns url-shortener.admin.handler
  (:require
    [hiccup2.core :as h]
    [clojure.core.async :as a :refer [chan sliding-buffer <!! sub unsub close!]]
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
     [:link {:rel "stylesheet" :href "/css/report.css"}]
     [:link {:rel "stylesheet" :href "/css/admin.css"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]
     [:script "window.addEventListener('pageshow', function(event) { if (event.persisted) { window.location.reload(); } });"]]
    [:body
     [:div.center
      [:div.sidebar
       [:nav.sidebar__aside.stack
        [:div.nav__title "Admin"]
        [:a.nav__link {:href "#stats"}     "Overview"]
        [:a.nav__link {:href "#groups"}     "Groups"]
        [:a.nav__link {:href "#countries"} "Countries"]]
       [:main.sidebar__main.stack {:data-init           "@get('/admin/stream')"
                                   :data-signals        "{connected: false}"
                                   :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
        [:div.cluster
         [:span.live-dot {:data-class "{active: $connected}"}]
         [:span {:style "font-size: var(--step--1); color: var(--color-muted)"}
          "Live"]]
        [:div.switcher {:id "stats"}
         [:div.box.stack
          [:span.stat__label "Total Links"]
          [:span.stat__value "--"]]
         [:div.box.stack
          [:span.stat__label "Total Groups"]
          [:span.stat__value "--"]]
         [:div.box.stack
          [:span.stat__label "Total Clicks"]
          [:span.stat__value "--"]]
         [:div.box.stack
          [:span.stat__label "Unique Visitors"]
          [:span.stat__value "--"]]]
        [:div.stack {:id "groups"}]
        [:div {:id "countries"}]]]]])) ; main, sidebar, center, body close + html5 + defn


(defn handle-admin-stream [pubsub request]
  (let [ch-atom  (atom nil)
        cleanup! (fn []
                   (when-let [ch @ch-atom]
                     (log/debug "admin stream closed")
                     (unsub (:publication pubsub) :click ch)
                     (close! ch)
                     (reset! ch-atom nil)))]
    (hk-gen/->sse-response request
      {hk-gen/on-open
       (fn [sse]
         (let [ch (chan (sliding-buffer 10))]
           (reset! ch-atom ch)
           (sub (:publication pubsub) :click ch)
           (try
             (d*/patch-elements! sse (render/render-stats))
             (d*/patch-elements! sse (render/render-groups))
             (d*/patch-elements! sse (render/render-countries))
             (loop []
               (when-let [event (<!! ch)]
                 (try
                   (d*/patch-elements! sse (render/render-stats))
                   (d*/patch-elements! sse (render/render-groups))
                   (d*/patch-elements! sse (render/render-countries))
                   (catch Exception e (log/error "push failed" (.getMessage e))))
                 (recur)))
             (catch Exception e (log/error "stream failed" (.getMessage e))))))
       hk-gen/on-close
       (fn [sse status]
         (log/debug "admin stream on-close" status)
         (cleanup!))})))


(defn handle-admin [_]
  {:status  200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body    (admin-page)})
