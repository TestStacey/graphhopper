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

import com.google.transit.realtime.GtfsRealtime;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public class WurstIT {

    @Test
    public void testMapdb() {
        DB make = DBMaker.newFileDB(new File("wurst.db")).transactionDisable().mmapFileEnable().asyncWriteEnable().make();

        Map<GtfsRealtime.TripDescriptor, int[]> boardEdgesForTrip = make.getHashMap("wurst");
        GtfsRealtime.TripDescriptor build = GtfsRealtime.TripDescriptor.newBuilder().setTripId("7732602").build();
        boardEdgesForTrip.put(build, new int[]{1,2,3});
        make.close();
        GtfsRealtime.TripDescriptor build2 = GtfsRealtime.TripDescriptor.newBuilder().setTripId("7732602").build();

        DB make2 = DBMaker.newFileDB(new File("wurst.db")).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
        int[] wurst = (int[]) make2.getHashMap("wurst").get(build2);
        System.out.println(Arrays.toString(wurst));
    }

}
