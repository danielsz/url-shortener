(ns url-shortener.reports.group
  (:require   
   [hiccup2.core :as h]
   [hiccup.page :refer [html5]]
   [taoensso.carmine :as redis]
   [url-shortener.shared.utils :refer [generate-token epoch-now display-name]]
   [clojure.tools.logging :as log]
   [ring.util.response :refer [response status]]
   
   [url-shortener.schema :refer [report-key group-links-key group-daily-key group-weekly-key group-monthly-key  group-countries-key group-key group-ips-key group-reports-key TTL-REPORT]]
   [url-shortener.shared.sse-fragments :refer [sparkline]]))

(defn create-group-report! [owner-id group-id]
  (let [actual-owner (redis/wcar nil (redis/hget (group-key group-id) "owner-id"))]
    (if (= actual-owner owner-id)
      (let [token (generate-token)]
        (redis/wcar nil
          (redis/hset    (report-key token)
                         "type"      "group"
                         "target-id" group-id
                         "owner-id"  owner-id
                         "created"   (epoch-now))
          (redis/expire  (report-key token) TTL-REPORT)
          (redis/sadd    (group-reports-key group-id) token))
        (response (str (System/getProperty "shortener.service") "report/" token)))
      (status 403))))

(defn group-report-page [token group-id]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (display-name group-id) " — Analytics")]
     [:link {:rel "stylesheet" :href "/css/tokens.css"}]
     [:link {:rel "stylesheet" :href "/css/layout.css"}]
     [:link {:rel "stylesheet" :href "/css/report.css"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div.center
      [:div.stack
       {:data-signals "{connected: false}"
        :data-init    (str "@get('/report/" token "/stream')")
        :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
       [:div.box
        [:span.report__description (display-name group-id)]]
       [:div.cluster
        [:span.live-dot {:data-class "{active: $connected}"}]
        [:span.stat__label "Live"]]
       [:div {:id "stats"}]
       [:div {:id "chart"}]
       [:div {:id "countries"}]
       [:div {:id "links"}]]]]))

(defn handle-group-report [token report]
  (let [group-id   (get report "target-id")]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (group-report-page token group-id)}))

(defn render-stats [group-id]
  (let [link-count (redis/wcar nil (redis/scard (group-links-key group-id)))
        [clicks unique-ips]
        (redis/wcar nil
          (redis/hget  (group-key group-id) "clicks")
          (redis/zcard (group-ips-key group-id)))]
    (str (h/html
      [:div {:id "stats" :class "switcher"}
       [:div.box.stack
        [:span.stat__label "Total Links"]
        [:span.stat__value (or link-count 0)]]
       [:div.box.stack
        [:span.stat__label "Total Clicks"]
        [:span.stat__value (or clicks "0")]]
       [:div.box.stack
        [:span.stat__label "Unique Visitors"]
        [:span.stat__value (or unique-ips 0)]]]))))

(defn- render-chart [group-id]
  (let [[daily weekly monthly] (redis/wcar nil
                                           (redis/hgetall (group-daily-key group-id))
                                           (redis/hgetall (group-weekly-key group-id))
                                           (redis/hgetall (group-monthly-key group-id)))]
    (str (h/html
      [:div {:id "chart" :class "stack"}
       [:div.box.stack
        [:span.stat__label "Daily"]
        (if (seq daily)
          (sparkline (apply hash-map daily))
          [:span.stat__muted "No data yet"])]
       [:div.box.stack
        [:span.stat__label "Weekly"]
        (if (seq weekly)
          (sparkline (apply hash-map weekly))
          [:span.stat__muted "No data yet"])]
       [:div.box.stack
        [:span.stat__label "Monthly"]
        (if (seq monthly)
          (sparkline (apply hash-map monthly))
          [:span.stat__muted "No data yet"])]]))))


(defn render-countries [group-id]
  (let [raw    (redis/wcar nil (redis/hgetall (group-countries-key group-id)))
        totals (update-vals (if (seq raw) (apply hash-map raw) {}) parse-long)
        sorted (sort-by val > totals)
        max-v  (apply max 1 (map val sorted))]
    (str (h/html
      [:div {:id "countries" :class "box stack"}
       [:span.stat__label "Top Countries"]
       (if (seq sorted)
         (for [[country cnt] (take 10 sorted)]
           [:div.country-row
            [:span country]
            [:span cnt]
            [:div.country-row__bar
             {:style (str "--pct:" (int (* 100 (/ cnt max-v))))}]])
         [:span.stat__muted "No data yet"])]))))

(defn render-links [group-id]
  (let [paths  (redis/wcar nil (redis/smembers (group-links-key group-id)))
        links  (when (seq paths)
                 (redis/wcar nil
                             (doseq [p paths]
                               (redis/hget p "url")
                               (redis/hget p "description")
                               (redis/hget p "clicks"))))]
    (str (h/html
      [:div {:id "links" :class "stack"}
       [:span.stat__label "Links"]
       (if (seq paths)
         (for [[path [url desc clicks]]
               (map vector paths (partition 3 links))]
           [:div.box.stack {:style "--stack-space: var(--space-s)"}
            [:div.cluster
             [:span.link-card__clicks (or clicks "0")]
             [:span.stat__label "clicks"]]
            [:div.link-card__url url]
            (when (seq desc)
              [:div.link-card__desc desc])])
         [:span.stat__muted "No links yet"])]))))
