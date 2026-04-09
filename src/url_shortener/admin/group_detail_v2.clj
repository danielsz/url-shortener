(ns url-shortener.admin.group-detail-v2
  (:require
    [hiccup.page :refer [html5]]
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
    [url-shortener.shared.analytics :as analytics]
    [url-shortener.shared.utils :refer [display-name]]
    [url-shortener.autotweet.shop-detail :as autotweet]
    [taoensso.carmine :as redis]
    [cheshire.core :as json]
    [ring.util.response :refer [redirect]]
    [url-shortener.schema :refer [group-links-key]]))

(defn- build-signals [group-id]
  (let [{:keys [platforms confirmed]} (analytics/group-platforms group-id)]
    (cond-> {:stats     (analytics/group-stats group-id)
             :countries (analytics/group-countries group-id)
             :platforms platforms
             :daily     (analytics/group-daily group-id)
             :links     (analytics/group-links group-id)
             :feed      []}
      (seq confirmed) (assoc :confirmed confirmed))))


(defn- group-detail-page-v2 [group-id]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (display-name group-id) " — Analytics")]
     [:link {:rel "stylesheet" :href "/css/v2.css"}]
     [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.js"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div {:data-signals "{connected:false,stats:{total_clicks:0,unique_visitors:0,links:0},countries:{},platforms:{},daily:{all:{}},links:[],feed:[],confirmed:{}}"
            :data-init    (str "@get('/admin/v2/group/" group-id "/stream')")
            :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}

      [:main.center
       [:div.stack
        [:header.header
         [:div.stack.stack--tight
          [:div.cluster.cluster--tight
           [:span.label-tiny "tuppu.net · " (display-name group-id) " ·"]
           [:span.live-dot {:data-class "{active: $connected}"}]
           [:span.label-tiny {:data-text "$connected ? 'live' : 'connecting…'"} "connecting..."]]
          [:div.total-clicks {:data-text "$stats.total_clicks.toLocaleString()"}]
          [:p.sub-label "total clicks"]]
         [:div.header-right "Q = quantitative · O = ordinal · N = nominal"]]

        [:div.dashboard-grid

         [:div.col-side

          [:div.pnl.stack
           [:div.pnl-title "Overview"]
           [:div.switcher.stat-switcher
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.total_clicks.toLocaleString()"}]
             [:div.stat-lbl "Clicks"]]
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.unique_visitors.toLocaleString()"}]
             [:div.stat-lbl "Visitors"]]
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.links.toLocaleString()"}]
             [:div.stat-lbl "Links"]]]]

          [:div.pnl.stack
           [:div.pnl-title "Top countries"]
           [:div.panel-body {:id "countries-panel"}]]

          [:div.pnl.stack
           [:div.pnl-title "Sources"]
           [:div.panel-body {:id "platforms-panel"}]
           [:p.note "No pie — angle encodes Q data poorly."]]          

          [:div.pnl.stack.pnl--confirmed.pnl--conditional
           {:id        "confirmed-pnl"
            :data-show "$confirmed && Object.keys($confirmed).length > 0"}
           [:div.pnl-title "Platform confirmation"]
           [:span.enc-tag "Observed platform ∈ intended targets"]
           [:div.panel-body {:id "confirmed-panel"}]]]
         
         
         [:div.col-main

          
          [:div.col-chart

           [:div.pnl.stack
            [:div.pnl-title "Clicks over time — daily"]
            [:div.chart-wrap
             [:canvas {:id "tsChart"}]]]]

          [:div.pnl.stack.pnl--conditional
           {:id        "feed-pnl"
            :data-show "$feed && $feed.length > 0"}
           [:div.pnl-title "Recent clicks"]
           [:div.panel-body {:id "feed-panel"}]]

          [:div.col-links
           [:div.pnl.stack
            [:div.pnl-title "Links by volume"]
            [:div.panel-body {:id "links-panel"}]]]]

]]
       [:div.back-link
           [:a {:href "/admin"} "← Admin Dashboard"]]]]
     [:script {:src "/js/group-dashboard.js"}]]))

(defn handle-group-detail-v2 [{{group-id :group-id} :path-params}]
  {:status  200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body    (group-detail-page-v2 group-id)})


(defn handle-group-detail-v2 [{{group-id :group-id} :path-params}]
  (let [links (redis/wcar nil (redis/smembers (group-links-key group-id)))]
    (if (= 1 (count links))
      (redirect (str "/admin/v2/link/" (first links)))
      {:status  200
       :headers {"Content-Type" "text/html"
                 "Cache-Control" "no-store"}
       :body    (group-detail-page-v2 group-id)})))


(defn handle-group-stream-v2 [pubsub {{group-id :group-id} :path-params :as request}]
  (let [ch-atom  (atom nil)
        feed-atom (atom [])
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
             (d*/patch-signals! sse (json/generate-string (build-signals group-id)))
             (loop []
               (when-let [event (async/<!! ch)]
                 (when (= (:group-id event) group-id)
                   (try
                     (let [path (:path event)
                           url  (redis/wcar nil (redis/hget path "url"))
                           item {:short (str (System/getProperty "shortener.service") path)
                                 :url   url
                                 :time  (-> (java.time.LocalTime/now) (.truncatedTo java.time.temporal.ChronoUnit/SECONDS) str)}]
                       (swap! feed-atom #(vec (take 5 (cons item %)))))
                     (d*/patch-signals! sse (json/generate-string (assoc (build-signals group-id) :feed @feed-atom)))
                     (catch Exception e (log/error "push failed" (.getMessage e)))))
                 (recur)))
             (catch Exception e (log/error "stream failed for" group-id (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "group stream v2 closed" group-id status)
         (cleanup!))})))
