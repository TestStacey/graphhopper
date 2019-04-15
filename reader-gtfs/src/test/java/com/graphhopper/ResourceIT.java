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

package com.graphhopper;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.gtfs.ws.LocationConverterProvider;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ResourceIT {

    private static final String GRAPH_LOC = "target/ResourceIT";
    private static GraphHopperGtfs graphHopper;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;
    private static GtfsStorage gtfsStorage;

    static {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        final FootFlagEncoder footFlagEncoder = new FootFlagEncoder();

        EncodingManager encodingManager = EncodingManager.create(Arrays.asList(carFlagEncoder, ptFlagEncoder, footFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, Collections.singleton("files/sample-feed.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();
    }


    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addProvider(new LocationConverterProvider())
            .setMapper(Jackson.newObjectMapper())
            .addResource(graphHopper)
            .build();

    @Test
    public void testStationStationQuery() throws Exception {
        final Response response = resources.target("/route?point=Stop(070201053201)&point=Stop(070201053801)&locale=en-US&vehicle=pt&weighting=fastest&elevation=false&pt.earliest_departure_time=2017-08-28T08%3A46%3A46.649Z&use_miles=false&points_encoded=false&pt.max_walk_distance_per_leg=1000&pt.max_transfer_distance_per_leg=0&pt.profile=true&pt.limit_solutions=5").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        System.out.println(ghResponse);
    }

    @Test
    public void testPointPointQuery() throws Exception {

        System.out.println(LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant());
        final Response response = resources.target("/route")
                .queryParam("point","36.914893,-116.76821") // NADAV stop
                .queryParam("point","36.914944,-116.761472") //NANAA stop
                .queryParam(Parameters.PT.EARLIEST_DEPARTURE_TIME, "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        System.out.println(response);

        assertEquals(200, response.getStatus());
        GHResponse json = response.readEntity(GHResponse.class);
        System.out.println(json);
    }

    @Test
    public void testRouteNotFoundCase() {
        final Response response = resources.target("/route?vehicle=pt&point=52.553423,13.435518&point=52.591982,13.305924&pt.earliest_departure_time=2017-09-01T09%3A47%3A00.000Z&pt.profile=true").request().buildGet().invoke();
        System.out.println(response.readEntity(String.class));
        assertEquals(200, response.getStatus());
    }

}
