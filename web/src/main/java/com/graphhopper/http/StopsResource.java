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
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.GraphHopperStorage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.CALC_POINTS;
import static com.graphhopper.util.Parameters.Routing.INSTRUCTIONS;
import static com.graphhopper.util.Parameters.Routing.WAY_POINT_MAX_DISTANCE;

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
    public GHResponse route(@Context UriInfo uriInfo, @PathParam("origin-stop-id") String origin, @PathParam("destination-stop-id") String destination) {
        GHRequest request = new GHRequest();
        initHints(request.getHints(), uriInfo.getQueryParameters());
        return graphHopperGtfs.route(origin, destination, request);
    }

    private void initHints(HintsMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.put(e.getKey(), e.getValue().get(0));
            } else {
                // Do nothing.
                // TODO: this is dangerous: I can only silently swallow
                // the forbidden multiparameter. If I comment-in the line below,
                // I get an exception, because "point" regularly occurs
                // multiple times.
                // I think either unknown parameters (hints) should be allowed
                // to be multiparameters, too, or we shouldn't use them for
                // known parameters either, _or_ known parameters
                // must be filtered before they come to this code point,
                // _or_ we stop passing unknown parameters alltogether..
                //
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
            }
        }
    }


}
