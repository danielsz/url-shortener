(ns url-shortener.admin.group-detail
  (:require
    [hiccup2.core :as h]
    [hiccup.page :refer [html5]]
    [taoensso.carmine :as redis]
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
    [url-shortener.schema :refer [group-key group-links-key group-ips-key]]
    [url-shortener.shared.utils :refer [display-name]]))

(defn- sorted-links [group-id]
  (let [paths   (redis/wcar nil (redis/smembers (group-links-key group-id)))
        results (when (seq paths)
                  (redis/wcar nil
                              (doseq [p paths]
                                (redis/hget p "url")
                                (redis/hget p "description")
                                (redis/hget p "clicks"))))]
    (->> (map vector paths (partition 3 results))
         (map (fn [[path [url desc clicks]]]
                {:path   path
                 :url    url
                 :desc   desc
                 :clicks (parse-long (or clicks "0"))}))
         (sort-by :clicks >))))

(defn- render-group-stats [group-id]
  (let [[clicks unique-ips link-count]
        (redis/wcar nil
          (redis/hget  (group-key group-id) "clicks")
          (redis/zcard (group-ips-key group-id))
          (redis/scard (group-links-key group-id)))]
    (str (h/html
      [:div {:id "group-stats" :class "switcher"}
       [:div.box.stack
        [:span.stat__label "Total Links"]
        [:span.stat__value (or link-count 0)]]
       [:div.box.stack
        [:span.stat__label "Total Clicks"]
        [:span.stat__value (or clicks "0")]]
       [:div.box.stack
        [:span.stat__label "Unique Visitors"]
        [:span.stat__value (or unique-ips 0)]]]))))

(defn- render-group-links [group-id]
  (let [links (sorted-links group-id)]
    (str (h/html
      [:div {:id "group-links" :class "stack"}
       [:span.stat__label "Links"]
       (if (seq links)
         (for [{:keys [path url desc clicks]} links]
           [:a.box.stack
            {:href  (str "/admin/link/" path)
             :style "--stack-space: var(--space-s)"}
            [:div.cluster
             [:span.link-card__clicks clicks]
             [:span.stat__label "clicks"]]
            [:div.link-card__url url]
            (when (seq desc)
              [:div.link-card__desc desc])])
         [:span.stat__muted "No links yet"])]))))

(defn- group-detail-page [group-id]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (display-name group-id) " — Links")]
     [:link {:rel "stylesheet" :href "/css/tokens.css"}]
     [:link {:rel "stylesheet" :href "/css/layout.css"}]
     [:link {:rel "stylesheet" :href "/css/admin.css"}]
     [:link {:rel "stylesheet" :href "/css/report.css"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]
     [:script "window.addEventListener('pageshow', function(event) { if (event.persisted) { window.location.reload(); } });"]]
    [:body
     [:div.center
      [:div.sidebar
       [:nav.sidebar__aside.stack
        [:a.nav__link {:href "/admin"} "← Admin"]
        [:div.nav__title (display-name group-id)]
        [:a.nav__link {:href "#group-stats"}  "Overview"]
        [:a.nav__link {:href "#group-links"}  "Links"]]
       [:main.sidebar__main.stack {:data-init           (str "@get('/admin/group/" group-id "/stream')")
                                   :data-signals        "{connected: false}"
                                   :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
        [:div.cluster
         [:span.live-dot {:data-class "{active: $connected}"}]
         [:span {:style "font-size: var(--step--1); color: var(--color-muted)"
                 :data-text "$connected ? 'Live' : 'connecting…'"} "connecting..."]]
        [:div {:id "group-stats"}]
        [:div {:id "group-links"}]]]]]))

(defn handle-group-detail [{{group-id :group-id} :path-params}]
  {:status  200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body    (group-detail-page group-id)})

(defn handle-group-stream [pubsub {{group-id :group-id} :path-params :as request}]
  (let [ch-atom  (atom nil)
        cleanup! (fn []
                   (when-let [ch @ch-atom]
                     (async/unsub (:publication pubsub) :analytics-update ch)
                     (async/close! ch)
                     (reset! ch-atom nil)))]
    (->sse-response request
      {on-open
       (fn [sse]
         (let [ch (async/chan (async/sliding-buffer 10))]
           (reset! ch-atom ch)
           (async/sub (:publication pubsub) :analytics-update ch)
           (try
             (d*/patch-elements! sse (render-group-stats group-id))
             (d*/patch-elements! sse (render-group-links group-id))
             (loop []
               (when-let [event (async/<!! ch)]
                 (when (= (:group-id event) group-id)
                   (try
                     (d*/patch-elements! sse (render-group-stats group-id))
                     (d*/patch-elements! sse (render-group-links group-id))
                     (catch Exception e (log/error "push failed" (.getMessage e)))))
                 (recur)))
             (catch Exception e (log/error "stream failed" (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "group stream closed" group-id status)
         (cleanup!))})))

