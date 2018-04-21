build: mvn package -DskipTests
build: curl -L -O http://download.geofabrik.de/north-america/us/oregon-latest.osm.pbf
build: curl -L -O https://developer.trimet.org/schedule/gtfs.zip
build: curl -L -O http://feed.rvtd.org/googleFeeds/static/google_transit.zip
build: java -Xmx6000m -jar web/target/graphhopper-web-0.11-SNAPSHOT.jar import trimet.yml