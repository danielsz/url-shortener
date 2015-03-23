(defproject url-shortener "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
		[http-kit "2.1.16"]
		[com.taoensso/carmine "2.6.2"]
		[commons-validator/commons-validator "1.4.0"]
                [environ "1.0.0"]]
  :plugins [[org.danielsz/lein-runit "0.1.0-SNAPSHOT"]
            [lein-environ "1.0.0"]] 
  :runit {:app-root "/opt"
          :service-root "/etc/sv"}
  :main ^:skip-aot url-shortener.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:env {:host "http://localhost:8080/"}}
             :production {:env {:host "http://dan.tuppu.net:8080/"}}})

