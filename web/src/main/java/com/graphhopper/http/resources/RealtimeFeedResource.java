/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.http.resources;

import com.conveyal.gtfs.model.Trip;
import com.google.protobuf.TextFormat;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.http.RealtimeFeedConfiguration;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.RealtimeFeed;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.PrintWriter;

@Path("realtime-feed")
public class RealtimeFeedResource {

    private final RealtimeFeed realtimeFeed;
    private final GtfsStorage staticGtfs;

    @Inject
    public RealtimeFeedResource(RealtimeFeed realtimeFeed, GtfsStorage staticGtfs) {
        this.staticGtfs = staticGtfs;
        this.realtimeFeed = realtimeFeed;
    }

    @GET
    @Produces("text/plain")
    public StreamingOutput dump() throws IOException {
        return output -> {
            PrintWriter writer = new PrintWriter(output);
            TextFormat.print(realtimeFeed.feedMessage, writer);
            writer.flush();
        };
    }

    @GET
    @Path("report")
    @Produces("text/plain")
    public StreamingOutput report() {
        return output -> {
            PrintWriter writer = new PrintWriter(output);
            GtfsRealtime.FeedMessage realtimeFeed = this.realtimeFeed.feedMessage;
            realtimeFeed.getEntityList().stream()
                .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                .map(GtfsRealtime.FeedEntity::getTripUpdate)
                .forEach(tripUpdate -> {
                    if (tripUpdate.getTrip().getScheduleRelationship() != GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED) {
                        Trip trip = staticGtfs.getGtfsFeeds().get("gtfs_0").trips.get(tripUpdate.getTrip().getTripId());
                        if (trip == null) {
                            writer.println("Not found:");
                            try {
                                TextFormat.print(tripUpdate, writer);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });
            writer.flush();

            realtimeFeed.getEntityList().stream()
                .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                .map(GtfsRealtime.FeedEntity::getTripUpdate)
                .filter(tripUpdate -> tripUpdate.getTrip().getScheduleRelationship() != GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED)
                .map(tripUpdate -> tripUpdate.getTrip().getTripId())
                .map(tripId -> staticGtfs.getGtfsFeeds().get("gtfs_0").trips.get(tripId))
                .map(trip -> trip.route_id)
                .map(routeId -> staticGtfs.getGtfsFeeds().get("gtfs_0").routes.get(routeId))
                .map(route -> route.agency_id)
                .distinct()
                .forEach(agency_id -> writer.println(agency_id));
            writer.flush();
        };
    }

}
