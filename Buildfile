build: mvn package -DskipTests
build: curl -L -O http://download.geofabrik.de/europe/germany/brandenburg-latest.osm.pbf
build: curl -L -O https://s3.eu-central-1.amazonaws.com/graphhopper-vbb-631760/1434582.zip
build: java -Xmx6000m -jar web/target/graphhopper-web-0.10-SNAPSHOT.jar import vbb.yml