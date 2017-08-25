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

package com.graphhopper.http.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;

import java.io.IOException;
import java.text.NumberFormat;

public class GHResponseSerializer extends JsonSerializer<GHResponse> {

    static class Attributes {
        private final boolean calcPoints;
        private final boolean pointsEncoded;
        private final boolean enableElevation;
        private final boolean enableInstructions;

        Attributes(boolean calcPoints, boolean pointsEncoded, boolean enableElevation, boolean enableInstructions) {
            this.calcPoints = calcPoints;
            this.pointsEncoded = pointsEncoded;
            this.enableElevation = enableElevation;
            this.enableInstructions = enableInstructions;
        }
    }

    @Override
    public void serialize(GHResponse ghRsp, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        final Attributes attributes = (Attributes) serializers.getAttribute(Attributes.class);
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("hints", ghRsp.getHints().toMap());
        // If you replace GraphHopper with your own brand name, this is fine.
        // Still it would be highly appreciated if you mention us in your about page!
        final ObjectNode info = json.putObject("info");
        info.putArray("copyrights")
                .add("GraphHopper")
                .add("OpenStreetMap contributors");
        //TODO:
//        info.put("took", Math.round(took * 1000));
        ArrayNode jsonPathList = json.putArray("paths");
        for (PathWrapper ar : ghRsp.getAll()) {
            ObjectNode jsonPath = jsonPathList.addObject();
            jsonPath.put("distance", Helper.round(ar.getDistance(), 3));
            jsonPath.put("weight", Helper.round6(ar.getRouteWeight()));
            jsonPath.put("time", ar.getTime());
            jsonPath.put("transfers", ar.getNumChanges());
            if (!ar.getDescription().isEmpty()) {
                jsonPath.putPOJO("description", ar.getDescription());
            }
            if (attributes.calcPoints) {
                jsonPath.put("points_encoded", attributes.pointsEncoded);
                if (ar.getPoints().getSize() >= 2) {
                    jsonPath.putPOJO("bbox", ar.calcBBox2D());
                }
                jsonPath.putPOJO("points", attributes.pointsEncoded ? WebHelper.encodePolyline(ar.getPoints(), attributes.enableElevation) : ar.getPoints().toLineString(attributes.enableElevation));
                if (attributes.enableInstructions) {
                    jsonPath.putPOJO("instructions", ar.getInstructions());
                }
                jsonPath.putPOJO("legs", ar.getLegs());
                jsonPath.putPOJO("details", ar.getPathDetails());
                jsonPath.put("ascend", ar.getAscend());
                jsonPath.put("descend", ar.getDescend());
            }
            jsonPath.putPOJO("snapped_waypoints", attributes.pointsEncoded ? WebHelper.encodePolyline(ar.getWaypoints(), attributes.enableElevation) : ar.getWaypoints().toLineString(attributes.enableElevation));
            if (ar.getFare() != null) {
                jsonPath.put("fare", NumberFormat.getCurrencyInstance().format(ar.getFare()));
            }
        }
        gen.writeObject(json);
    }
}
