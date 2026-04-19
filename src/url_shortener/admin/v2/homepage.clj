(ns url-shortener.admin.v2.homepage
  (:require
   [hiccup2.core :as h]
   [hiccup.page :refer [html5 doctype]]
   [ring.util.response :refer [response content-type]]
   [clojure.tools.logging :as log]))



;; ============================================================================
;; tuppu.net · homepage · views/homepage.clj
;; Renders via hiccup2 — call (page) and wrap in your Ring response.
;; CSS lives in homepage.css (extends v2.css tokens).
;; ============================================================================

;; ---------------------------------------------------------------------------
;; Primitives
;; ---------------------------------------------------------------------------

(defn live-dot
  ([]     (live-dot {}))
  ([opts] [:span (merge {:class "live-dot live-dot--blue"} opts)]))

(defn btn-cta [text]
  [:button {:class "btn btn--cta"} text])

(defn btn-primary
  ([text]      (btn-primary text {}))
  ([text opts] [:button (merge {:class "btn btn--primary"} opts) text]))

(defn btn-ghost [text]
  [:button {:class "btn btn--ghost"} text])

(defn section-label [text]
  [:p {:class "section-label"} text])

;; ---------------------------------------------------------------------------
;; Nav
;; ---------------------------------------------------------------------------

(defn nav []
  [:nav {:class "hp-nav"}
   [:div {:class "center hp-nav__inner"}
    [:span {:class "wordmark"}
     "tuppu" [:span {:class "wordmark__dot"} ".net"]]
    [:ul {:class "hp-nav__links"}
     [:li [:a {:href "#"} "docs"]]
     [:li [:a {:href "#"} "pricing"]]
     [:li (btn-cta "sign in →")]]]])

;; ---------------------------------------------------------------------------
;; Hero
;; ---------------------------------------------------------------------------

(defn ticker-stat [value label]
  [:span {:class "ticker-stat"}
   [:strong {:id (when (= label "clicks today") "click-count")} value]
   " " label])

(defn hero []
  [:section {:class "hero"}
   [:div {:class "center hero__inner"}

    [:div {:class "hero__eyebrow"}
     (live-dot)
     [:span "live · link analytics · platform intelligence"]]

    [:h1 {:class "hero__headline"}
     "Short links." [:br] [:em "Deep signal."]]

    [:p {:class "hero__sub"}
     "Every link you shorten carries an intent — Twitter, Bluesky, an email newsletter. "
     "Tuppu tracks which platform actually delivered, in real time."]

    [:div {:class "hero__cta-row"}
     (btn-primary "start tracking →")
     (btn-ghost "see a live dashboard ↗")]

    [:div {:class "hero__ticker"}
     (ticker-stat "4,984" "clicks today")
     (ticker-stat "239"   "active links")
     (ticker-stat "1,081" "unique visitors")
     (ticker-stat "8"     "platforms tracked")]]])

;; ---------------------------------------------------------------------------
;; How it works
;; ---------------------------------------------------------------------------

(defn concept-step [{:keys [num title body example]}]
  [:div {:class "concept-step"}
   [:span {:class "concept-step__num"} num]
   [:h2  {:class "concept-step__title"} title]
   [:p   {:class "concept-step__body"} body]
   [:div {:class "concept-step__example"} example]])

(defn how-it-works []
  [:section {:class "hp-section"}
   [:div {:class "center"}
    (section-label "how it works")
    [:div {:class "concept-grid"}

     (concept-step
      {:num     "01 ·"
       :title   "Shorten"
       :body    "Paste your destination URL. We generate a clean, memorable short link
                 ready to share anywhere."
       :example [:span "→ " [:code "tuppu.net/dg4k2"] " → your product page"]})

     (concept-step
      {:num     "02 ·"
       :title   "Target"
       :body    "Tell us where you're publishing: Twitter, Bluesky, a newsletter, your bio link.
                 Tuppu records your intent."
       :example [:span "platforms: " [:code "twitter"] " · " [:code "bluesky"] " · " [:code "email"]]})

     (concept-step
      {:num     "03 ·"
       :title   "Measure"
       :body    "Watch clicks arrive live. See exactly which platform drove traffic —
                 against your declared intent, not just a raw referrer string."
       :example [:span "twitter: " [:code "68%"] " · bluesky: " [:code "24%"] " · other: " [:code "8%"]]})]]])

;; ---------------------------------------------------------------------------
;; Dashboard preview
;; ---------------------------------------------------------------------------

(defn platform-row [{:keys [name color width count]}]
  [:div {:class "platform-row"}
   [:div {:class "plat-name"}
    [:span {:class "plat-dot" :style (str "background:" color)}]
    name]
   [:div {:class "plat-bar-track"}
    [:div {:class "plat-bar-fill" :style (str "width:" width "%;background:" color)}]]
   [:span {:class "plat-count"} count]])

(def platform-data
  [{:name "twitter / x" :color "var(--c-twitter)"  :width 68 :count "3,389"}
   {:name "bluesky"     :color "var(--c-bluesky)"   :width 24 :count "1,197"}
   {:name "mastodon"    :color "var(--c-mastodon)"  :width  5 :count "249"}
   {:name "direct"      :color "var(--c-direct)"    :width  3 :count "149"}])

(defn dashboard-preview []
  [:section {:class "hp-section"}
   [:div {:class "center"}
    [:div {:class "preview-header"}
     (section-label "live dashboard")
     [:span {:class "text-tiny text-muted"} "real data, anonymized"]]

    [:div {:class "preview-frame"}

     ;; browser chrome
     [:div {:class "preview-chrome"}
      [:div {:class "chrome-dots"}
       [:div {:class "chrome-dot"}]
       [:div {:class "chrome-dot"}]
       [:div {:class "chrome-dot"}]]
      [:span {:class "chrome-url"} "tuppu.net/admin/v2/group/…"]]

     ;; dashboard body
     [:div {:class "preview-body"}

      ;; headline stat
      [:div {:class "dash-title-row"}
       [:div
        [:div {:class "dash-total"} "4,984"]
        [:div {:class "dash-label"} "total clicks"]]
       [:div {:class "dash-live"}
        (live-dot)
        [:span "live"]]]

      ;; sub stats
      [:div {:class "dash-stats"}
       [:div {:class "dash-stat"}
        [:span {:class "dash-stat__val"} "1,081"]
        [:span {:class "dash-stat__lbl"} "visitors"]]
       [:div {:class "dash-stat"}
        [:span {:class "dash-stat__val"} "239"]
        [:span {:class "dash-stat__lbl"} "links"]]
       [:div {:class "dash-stat"}
        [:span {:class "dash-stat__val"} "4.6×"]
        [:span {:class "dash-stat__lbl"} "clicks / visitor"]]]

      ;; platform breakdown
      [:div
       [:p {:class "platform-section-title"} "by platform"]
       (map platform-row platform-data)]]]]])

;; ---------------------------------------------------------------------------
;; Features
;; ---------------------------------------------------------------------------

(defn feature-item [{:keys [title body]}]
  [:div {:class "feature-item"}
   [:h3 {:class "feature-item__title"} title]
   [:p  {:class "feature-item__body"} body]])

(def feature-data
  [{:title "Platform attribution"
    :body  "Declare your intended targets when you shorten. See clicks confirmed
            against those targets, not just a raw referrer log."}
   {:title "Live streaming"
    :body  "The dashboard updates in real time via server-sent events.
            No polling, no refresh, no lag. Watch your campaign land."}
   {:title "Group analytics"
    :body  "Organise links into groups — a campaign, a product, a season.
            Compare performance across the entire group at a glance."}
   {:title "No noise"
    :body  "A dashboard designed after Bertin's visual encoding principles.
            Every pixel encodes meaning. Nothing decorative, nothing redundant."}])

(defn features []
  [:section {:class "hp-section"}
   [:div {:class "center"}
    (section-label "built for intent")
    [:div {:class "features-grid"}
     (map feature-item feature-data)]]])

;; ---------------------------------------------------------------------------
;; CTA
;; ---------------------------------------------------------------------------

(defn cta []
  [:section {:class "hp-section hp-section--no-border hp-section--center"}
   [:div {:class "center"}
    [:div {:class "cta-stack"}
     (section-label "get started")
     [:h2 {:class "cta-headline"}
      "Know where your audience actually comes from."]
     (btn-primary "create your first link →" {:class "btn btn--primary btn--primary-lg"})
     [:p {:class "text-muted text-tiny"} "free to start · no credit card"]]]])

;; ---------------------------------------------------------------------------
;; Footer
;; ---------------------------------------------------------------------------

(defn footer []
  [:footer {:class "hp-footer"}
   [:div {:class "center hp-footer__inner"}
    [:span {:class "wordmark"}
     "tuppu" [:span {:class "wordmark__dot"} ".net"]]
    [:ul {:class "hp-footer__links"}
     [:li [:a {:href "#"} "privacy"]]
     [:li [:a {:href "#"} "terms"]]
     [:li [:a {:href "#"} "api"]]
     [:li [:a {:href "#"} "status"]]]
    [:span "Q = quantitative · O = ordinal · N = nominal"]]])

;; ---------------------------------------------------------------------------
;; Live ticker script
;; ---------------------------------------------------------------------------

(defn ticker-script []
  [:script
   (h/raw
    "var el=document.getElementById('click-count'),n=4984;
     setInterval(function(){
       if(Math.random()<0.3){
         n+=Math.floor(Math.random()*3)+1;
         el.textContent=n.toLocaleString();
       }
     },2800);")])

;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(defn head []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:title "tuppu.net — link analytics with platform intelligence"]
   ;; base design system — must load before homepage.css
   [:link {:rel "stylesheet" :href "/css/v2/v2.css"}]
   [:link {:rel "stylesheet" :href "/css/v2/homepage.css"}]
   [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]])

(defn page []
  (str
   (h/html {:mode :html}
     (doctype :html5)
     [:html {:lang "en"}
      (head)
      [:body
       (nav)
       (hero)
       (how-it-works)
       (dashboard-preview)
       (features)
       (cta)
       (footer)
       (ticker-script)]])))


(defn serve [_]
  (-> (response (page))
      (content-type "text/html")))
