FROM openjdk:8-jre

WORKDIR /var/graphhopper

ADD graphhopper-web-0.10-SNAPSHOT.jar /var/graphhopper/graphhopper-dw.jar
ADD vbb.yml /var/graphhopper/vbb.yml
ADD brandenburg-latest.osm.pbf /var/graphhopper/brandenburg-latest.osm.pbf
ADD 1190951.zip /var/graphhopper/1190951.zip

EXPOSE 8989 8990

ENTRYPOINT ["java", "-Xmx6000m", "-jar", "graphhopper-dw.jar", "server", "vbb.yml"]