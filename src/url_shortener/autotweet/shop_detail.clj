(ns url-shortener.autotweet.shop-detail
  (:require
    [hiccup.page :refer [html5]]
    [clojure.tools.logging :as log]
    [url-shortener.shared.utils :refer [display-name]]))


(defn shop-dasboard [group-id]
  [:div
   [:div.header
    [:div
     [:div.label-tiny
      "tuppu.net · " (display-name group-id) " · "
      [:span.live-dot {:data-class "{disconnected: !$connected}"}]
      [:span {:data-text "$connected ? 'live' : 'connecting…'"} "live"]]
     [:div.total-clicks {:id "total-clicks"} "—"]
     [:div {:style "font-size:10px;color:var(--text-t);"} "total clicks"]]
    [:div.header-right
     "Encoding per Bertin (1967)" [:br]
     "Q = quantitative · O = ordinal · N = nominal" [:br]
     "hue = nominal only · value = quantitative only"]]

   [:div.grid
    [:div.col

     ;; Time series
     [:div.pnl
      [:div.pnl-title "Clicks over time — daily"]
      [:div
       [:span.enc-tag "Position X → date (O)"]
       [:span.enc-tag "Position Y → clicks (Q)"]]
      [:div.chart-wrap [:canvas {:id "tsChart"}]]
      [:div {:id "legend"}]]

     ;; Links by volume
     [:div.pnl
      [:div.pnl-title "Links by volume"]
      [:div
       [:span.enc-tag "Position Y → rank (O)"]
       [:span.enc-tag "Bar length → clicks (Q)"]]
      [:div {:id "links-panel" :style "margin-top:14px;display:flex;flex-direction:column;gap:10px;"}]]

     ;; Countries
     [:div.pnl
      [:div.pnl-title "Top countries"]
      [:div
       [:span.enc-tag "Position Y → rank (O)"]
       [:span.enc-tag "Bar length → clicks (Q)"]]
      [:div {:id "countries-panel" :style "margin-top:10px;display:flex;flex-direction:column;gap:4px;"}]]]

    [:div.col

     ;; Stats
     [:div.pnl
      [:div.pnl-title "Overview"]
      [:div.stat-grid
       [:div.stat-cell
        [:div.stat-val {:id "stat-clicks"} "—"]
        [:div.stat-lbl "Clicks"]]
       [:div.stat-cell
        [:div.stat-val {:id "stat-visitors"} "—"]
        [:div.stat-lbl "Visitors"]]
       [:div.stat-cell
        [:div.stat-val {:id "stat-links"} "—"]
        [:div.stat-lbl "Links"]]]]

     ;; Sources
     [:div.pnl
      [:div.pnl-title "Sources"]
      [:div
       [:span.enc-tag "Position Y → source (N→O)"]
       [:span.enc-tag "Bar → clicks (Q)"]
       [:span.enc-tag "Hue → source (N)"]]
      [:div {:id "platforms-panel" :style "margin-top:14px;display:flex;flex-direction:column;gap:10px;"}]
      [:div.note "No pie — angle encodes Q data poorly."]]

     ;; Confirmed panel — only rendered when signal is present
     [:div.pnl.confirmed-pnl {:id "confirmed-pnl" :style "display:none;"}
      [:div.pnl-title "Platform confirmation"]
      [:div [:span.enc-tag "Observed platform ∈ intended targets"]]
      [:div {:id "confirmed-panel" :style "margin-top:10px;display:flex;flex-direction:column;gap:4px;"}]]

     ;; Back link
     [:div {:style "font-size:9px;color:var(--text-t);padding:8px 0;"}
      [:a {:href "/admin" :style "color:var(--text-t);"} "← Admin"]]]]])
