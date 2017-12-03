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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.graphhopper.reader.gtfs.GtfsStorage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;

@Path("static-feed")
public class StaticFeedResource {

    private final GtfsStorage gtfsStorage;

    @Inject
    public StaticFeedResource(GtfsStorage gtfsStorage) {
        this.gtfsStorage = gtfsStorage;
    }

    @GET
    @Produces("text/plain")
    @Path("trips/{trip_id}")
    public StreamingOutput getTrip(@PathParam("trip_id") String trip_id) throws GTFSFeed.FirstAndLastStopsDoNotHaveTimes {
        return output -> {
            PrintWriter out = new PrintWriter(output);
            Trip trip = gtfsStorage.getGtfsFeeds().get("gtfs_0").trips.get(trip_id);
            Iterable<StopTime> interpolatedStopTimesForTrip = getInterpolatedStoptimesForTrip(trip_id);
            for (StopTime stopTime : interpolatedStopTimesForTrip) {
                Stop stop = gtfsStorage.getGtfsFeeds().get("gtfs_0").stops.get(stopTime.stop_id);
                out.printf("Stop(%s) %f, %f %s %s\n", stopTime.stop_id, stop.stop_lat, stop.stop_lon, convertToGtfsTime(stopTime.arrival_time), convertToGtfsTime(stopTime.departure_time));
            }
            out.flush();
        };
    }

    public Iterable<StopTime> getInterpolatedStoptimesForTrip(@PathParam("trip_id") String trip_id) {
        try {
            return gtfsStorage.getGtfsFeeds().get("gtfs_0").getInterpolatedStopTimesForTrip(trip_id);
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
            throw new RuntimeException(firstAndLastStopsDoNotHaveTimes);
        }
    }

}
