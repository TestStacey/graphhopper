build: mvn package -DskipTests
build: curl -L -O http://download.geofabrik.de/north-america/us/oregon-latest.osm.pbf
build: curl -L -O https://developer.trimet.org/schedule/gtfs.zip
build: java -Xmx6000m -jar web/target/graphhopper-web-0.10-SNAPSHOT.jar import trimet.yml