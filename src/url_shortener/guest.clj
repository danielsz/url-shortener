(ns url-shortener.guest
  (:require
    [clojure.string :as str]
    [hiccup2.core :as h]
    [hiccup.page :refer [doctype include-css]]
    [ring.util.response :refer [response content-type bad-request]]
    [url-shortener.shortener :refer [shorten!]]
    [url-shortener.shared.utils :refer [url-validator generate-token find-or-create-report! dev?]]
    [url-shortener.schema :refer [reports-key]]
    [url-shortener.admin.v2.start :as start-page]))

;; ---------------------------------------------------------------------------
;; Guest identity
;; ---------------------------------------------------------------------------

(defn- generate-anon-id []
  (str "anon:" (generate-token)))

(defn- get-anon-id [request]
  (get-in request [:cookies "anon-id" :value]))

(defn- anon-cookie [anon-id]
  {"anon-id" {:value     anon-id
              :http-only true
              :same-site :lax
              :secure    (not (dev?))
              :max-age   (* 365 86400)
              :path      "/"}})

;; ---------------------------------------------------------------------------
;; Post-shorten page
;; ---------------------------------------------------------------------------

(defn- copy-button [target-id]
  [:button {:class    "btn--copy"
            :onclick  (str "navigator.clipboard.writeText(document.getElementById('" target-id "').textContent);"
                           "this.textContent='copied!';"
                           "setTimeout(()=>this.textContent='copy',1500)")}
   "copy"])

(defn- post-shorten-page [short-url report-url path]
  (str
    (h/html {:mode :html}
      (doctype :html5)
      [:html {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
        [:title "Your link is ready — tuppu.net"]
        (include-css "/css/v2/tokens.css"
                     "/css/v2/reset.css"
                     "/css/v2/primitives.css"
                     "/css/v2/components.css"
                     "/css/v2/start.css")]
       [:body {:class "start-page cover"}
        (start-page/nav)
        [:main {:class "cover__principal center"}
         [:div {:class "start-card"}
          [:div {:class "start-card__header"}
           [:h1 {:class "start-card__title"} "Your link is ready."]
           [:p  {:class "start-card__sub"}
            "Share it anywhere. Your analytics dashboard updates live as clicks arrive."]]

          [:div {:class "stack"}

           ;; Short URL
           [:div {:class "field"}
            [:span {:class "field__label"} "your short link"]
            [:div {:class "cluster cluster--tight"}
             [:code {:class "field__input field__input--display" :id "short-url"} short-url]
             (copy-button "short-url")]]

           ;; Report URL
           [:div {:class "field"}
            [:span {:class "field__label"} "analytics dashboard"]
            [:div {:class "cluster cluster--tight"}
             [:code {:class "field__input field__input--display" :id "report-url"} report-url]
             (copy-button "report-url")]
            [:p {:class "field__hint"}
             "Public — share it with anyone. Temporary — save it before it expires."]]

           ;; CTA
           [:div {:class "field"}
            [:span {:class "field__label"} "save permanently"]
            [:p {:class "field__hint"}
             "Sign up to keep your link and dashboard forever."]
            [:div {:class "cluster cluster--xs"}
             [:a {:href  (str "/register?claim=" path)
                  :class "btn btn--primary"} "sign up →"]
             [:a {:href  (str "/login?claim=" path)
                  :class "btn btn--ghost"} "sign in"]]]]]]]])))

;; ---------------------------------------------------------------------------
;; Handlers
;; ---------------------------------------------------------------------------

(defn handle-start [request]
  (let [anon-id  (or (get-anon-id request) (generate-anon-id))
        resp     (-> (response (start-page/page))
                     (content-type "text/html")
                     (assoc :cookies (anon-cookie anon-id)))]
    resp))

(defn handle-shorten [request]
  (let [anon-id  (or (get-anon-id request) (generate-anon-id))
        url      (get-in request [:params :url])]
    (if (.isValid url-validator url)
      (let [req      (assoc-in request [:params :owner-id] anon-id)
            path     (shorten! req)
            report   (find-or-create-report! path reports-key)
            service  (System/getProperty "shortener.service")
            short-url  (str service path)
            report-url (str service "report/" report)]
        (-> (response (post-shorten-page short-url report-url path))
            (content-type "text/html")
            (assoc :cookies (anon-cookie anon-id))))
      (bad-request "Invalid URL provided"))))

