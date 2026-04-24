(ns url-shortener.admin.v2.start
  (:require [hiccup2.core :as h]
            [hiccup.page :refer [doctype include-css]]
            [ring.util.response :refer [response content-type]]))


;; ============================================================================
;; tuppu.net · /start page · views/start.clj
;;
;; Flow: paste URL → declare platforms/targets → shorten → live dashboard
;;
;; No JavaScript. Chip selection state is expressed via CSS :has(input:checked).
;; Custom targets are a plain text input submitted alongside the checkboxes.
;; Server validates all inputs; the submit button is always enabled.
;; ============================================================================


;; ---------------------------------------------------------------------------
;; Platform data
;; Order: roughly by expected frequency for a typical user
;; "direct", "other", "twitter-aggregator" excluded — inferred states,
;; not publishing intentions.
;; ---------------------------------------------------------------------------

(def known-platforms
  [{:id "twitter"   :label "twitter / x" :color "var(--c-twitter)"}
   {:id "bluesky"   :label "bluesky"     :color "var(--c-bluesky)"}
   {:id "mastodon"  :label "mastodon"    :color "var(--c-mastodon)"}
   {:id "instagram" :label "instagram"   :color "var(--c-instagram)"}
   {:id "facebook"  :label "facebook"    :color "var(--c-facebook)"}
   {:id "linkedin"  :label "linkedin"    :color "var(--c-linkedin)"}
   {:id "pinterest" :label "pinterest"   :color "var(--c-pinterest)"}
   {:id "reddit"    :label "reddit"      :color "var(--c-other)"}
   {:id "youtube"   :label "youtube"     :color "var(--c-other)"}
   {:id "google"    :label "google"      :color "var(--c-google)"}
   {:id "email"     :label "email"       :color "var(--c-direct)"}])


;; ---------------------------------------------------------------------------
;; Primitives
;; ---------------------------------------------------------------------------


(defn nav []
  [:nav {:class "hp-nav"}
   [:div {:class "spread center"}
    [:a {:href "/" :class "wordmark"}
     "tuppu" [:span {:class "wordmark__dot"} ".net"]]
    [:ul {:class "cluster cluster--m hp-nav__links"}
     [:li [:a {:href "/register"} "sign up"]]
     [:li [:a {:href "/login"}    "sign in"]]]]])


(defn platform-chip [{:keys [id label color]}]
  [:label {:class "plat-chip"
           :style (str "--chip-color:" color)}
   [:input {:type  "checkbox"
            :name  "target"
            :value id}]
   [:span {:class "plat-chip__dot"}]
   [:span {:class "plat-chip__name"} label]])


;; ---------------------------------------------------------------------------
;; Page sections
;; ---------------------------------------------------------------------------

(defn url-field []
  [:div {:class "field"}
   [:label {:class "field__label" :for "url"} "destination URL"]
   [:input {:class        "field__input"
            :id           "url"
            :name         "url"
            :type         "url"
            :inputmode    "url"
            :placeholder  "https://your-link-here.com/page"
            :autocomplete "off"
            :required     true}]
   [:p {:class "field__hint"}
    "paste the full URL you want to share"]])

(defn platform-picker []
  [:div {:class "field platform-picker"}
   [:span {:class "field__label"} "where are you posting?"]
   [:p {:class "field__hint"}
    "select all platforms — we'll track which ones actually drove clicks"]
   [:div {:class "platform-grid"}
    (map platform-chip known-platforms)]
   [:div {:class "field custom-target-row"}
    [:label {:class "field__label" :for "custom-target"} "other platform"]
    [:input {:class        "field__input"
             :id           "custom-target"
             :name         "target"
             :type         "text"
             :placeholder  "e.g. newsletter, podcast, tiktok…"
             :autocomplete "off"
             :maxlength    "64"}]
    [:p {:class "field__hint"}
     "one additional platform, if not listed above"]]])

(defn submit-row []
  [:div {:class "submit-row"}
   [:p {:class "submit-row__note"}
    "Your link is ready in seconds." [:br]
    "No account needed to start."]
   [:button {:class "btn--submit" :type "submit"}
    "shorten →"]])


;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(defn head []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:title "shorten a link — tuppu.net"]
   (if true ; (dev?)
     (include-css "/css/v2/tokens.css"
                  "/css/v2/reset.css"
                  "/css/v2/primitives.css"
                  "/css/v2/components.css"
                  "/css/v2/start.css")
     (include-css "/css/styles.min.css"))])

(defn page []
  (str
   (h/html {:mode :html}
     (doctype :html5)
     [:html {:lang "en"}
      (head)
      [:body {:class "start-page cover"}
       (nav)
       [:main {:class "cover__principal center"}
        [:div {:class "center" :style "width:100%"}
         [:a {:href "/" :class "start-back"} "← back"]
         [:div {:class "start-card"}
          [:div {:class "start-card__header"}
           [:h1 {:class "start-card__title"} "Shorten a link."]
           [:p  {:class "start-card__sub"}
            "Tell us where you're posting — we'll track which platform "
            "actually drives clicks."]]
          [:form {:method "post" :action "/shorten" :id "start-form"}
           (url-field)
           (platform-picker)
           (submit-row)]]]]]])))

(defn serve [_]
  (-> (response (page))
     (content-type "text/html")))
