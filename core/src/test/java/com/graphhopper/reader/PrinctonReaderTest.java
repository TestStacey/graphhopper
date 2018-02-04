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
package com.graphhopper.reader;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeExplorer;
import org.junit.Test;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class PrinctonReaderTest {
    GHJson json = new GHJsonFactory().create();
    private EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("car").build();
    private BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
    private EdgeFilter carOutEdges = new DefaultEdgeFilter(accessEnc, true, false);

    @Test
    public void testRead() {
        Graph graph = new GraphBuilder(encodingManager, json).create();
        new PrinctonReader(graph, accessEnc, encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED)).
                setStream(PrinctonReader.class.getResourceAsStream("tinyEWD.txt")).read();
        assertEquals(8, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(2, count(explorer.setBaseNode(0)));
        assertEquals(3, count(explorer.setBaseNode(6)));
    }

    @Test
    public void testMediumRead() throws IOException {
        Graph graph = new GraphBuilder(encodingManager, json).create();
        new PrinctonReader(graph, accessEnc, encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED)).
                setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream("mediumEWD.txt.gz"))).read();
        assertEquals(250, graph.getNodes());
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        assertEquals(13, count(explorer.setBaseNode(244)));
        assertEquals(11, count(explorer.setBaseNode(16)));
    }
}