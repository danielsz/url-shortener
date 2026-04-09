(ns url-shortener.admin.link-detail-v2
  (:require
    [hiccup.page :refer [html5]]
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
    [url-shortener.shared.analytics :as analytics]
    [taoensso.carmine :as redis]
    [cheshire.core :as json]
    [ring.util.response :refer [redirect]]))

(defn- build-signals [path]
  (let [{:keys [platforms confirmed]} (analytics/link-platforms path)]
    (cond-> {:stats     (analytics/link-stats path)
             :countries (analytics/link-countries path)
             :platforms platforms
             :daily     (analytics/link-daily path)
             :feed      []}
      (seq confirmed) (assoc :confirmed confirmed))))

(defn- link-detail-page-v2 [path]
  (let [[url desc] (redis/wcar nil
                     (redis/hget path "url")
                     (redis/hget path "description"))]
    (html5
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:title (str (or desc url) " — Analytics")]
       [:link {:rel "stylesheet" :href "/css/v2.css"}]
       [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.js"}]
       [:script {:type "module"
                 :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
      [:body
       [:div {:data-signals "{connected:false,stats:{total_clicks:0,unique_visitors:0},countries:{},platforms:{},daily:{all:{}},feed:[],confirmed:{}}"
              :data-init    (str "@get('/admin/v2/link/" path "/stream')")
              :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}

        [:main.center
         [:div.stack
          [:header.header
           [:div.stack.stack--tight
            [:div.cluster.cluster--tight
             [:span.label-tiny "tuppu.net · " path " ·"]
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
               [:div.stat-lbl "Visitors"]]]]

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

            [:div.pnl.stack
             [:div.pnl-title "Destination"]
             [:div.panel-body
              [:a.link-url {:href url :target "_blank"} url]
              (when (seq desc)
                [:div.link-desc desc])]]]]

         [:div.back-link
          [:a {:href "/admin"} "← Admin Dashboard"]]]]
        [:script {:src "/js/group-dashboard.js"}]]])))

(defn handle-link-detail-v2 [{{path :path} :path-params}]
  {:status  200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body    (link-detail-page-v2 path)})

(defn handle-link-stream-v2 [pubsub {{path :path} :path-params :as request}]
  (let [ch-atom   (atom nil)
        feed-atom (atom [])
        cleanup!  (fn []
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
             (d*/patch-signals! sse (json/generate-string (build-signals path)))
             (loop []
               (when-let [event (async/<!! ch)]
                 (when (= (:path event) path)
                    (try
                     (let [item {:short (str (System/getProperty "shortener.service") path)
                                 :url   (redis/wcar nil (redis/hget path "url"))
                                 :time  (-> (java.time.LocalTime/now) (.truncatedTo java.time.temporal.ChronoUnit/SECONDS) str)}]
                       (swap! feed-atom #(vec (take 5 (cons item %)))))
                     (d*/patch-signals! sse (json/generate-string (assoc (build-signals path) :feed @feed-atom)))
                     (catch Exception e (log/error "push failed" (.getMessage e)))))
                 (recur)))
             (catch Exception e (log/error "stream failed for" path (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "link stream v2 closed" path status)
         (cleanup!))})))
