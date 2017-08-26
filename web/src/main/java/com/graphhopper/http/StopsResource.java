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

package com.graphhopper.http;

import com.conveyal.gtfs.model.Stop;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.ValidGHRequest;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.storage.GraphHopperStorage;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Path("stops")
@Produces(MediaType.APPLICATION_JSON)
public class StopsResource {

    private final GtfsStorage gtfsStorage;
    private final GraphHopperGtfs graphHopperGtfs;

    @Inject
    StopsResource(GraphHopperStorage graphHopperStorage, GraphHopperAPI graphHopperAPI) {
        gtfsStorage = (GtfsStorage) graphHopperStorage.getExtension();
        graphHopperGtfs = (GraphHopperGtfs) graphHopperAPI;
    }

    @GET
    public Collection<Stop> getStations() {
        return gtfsStorage.getGtfsFeeds().get("gtfs_0").stops.values();
    }

    @Path("{origin-stop-id}/route-to/{destination-stop-id}")
    @GET
    public GHResponse route(@ValidGHRequest @BeanParam GHRequest request, @PathParam("origin-stop-id") String origin, @PathParam("destination-stop-id") String destination) {
        return graphHopperGtfs.route(origin, destination, request);
    }

}
