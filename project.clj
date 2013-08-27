(defproject esper-hornetq "0.1.0-SNAPSHOT"
  :description "app to use esper dinamically through hornetq"
  :url "http://github.com/marianoguerra/esper-hornetq"
  :license {:name "GPL v2"
            :url "https://www.gnu.org/licenses/gpl-2.0.html"}
  :main esper-hornetq.core/main
  :profiles {:uberjar {:aot :all}}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clj-esper/clj-esper "1.0.2-SNAPSHOT"]
                 [eventfabric/core-immutant "0.1.1-SNAPSHOT"]
                 [org.immutant/immutant-messaging "1.0.0"]])
