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

package com.graphhopper.gtfs.dropwizard;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.storage.GraphHopperStorage;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RealtimeFeedCache implements Factory<RealtimeFeed> {

    private GraphHopperStorage graphHopperStorage;
    private GtfsStorage gtfsStorage;
    private PtFlagEncoder ptFlagEncoder;
    private RealtimeBundleConfiguration bundleConfiguration;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private LoadingCache<String, RealtimeFeed> cache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .refreshAfterWrite(1, TimeUnit.MINUTES)
            .build(new CacheLoader<String, RealtimeFeed>() {
                public RealtimeFeed load(String key) {
                    return fetchFeedsAndCreateGraph();
                }

                @Override
                public ListenableFuture<RealtimeFeed> reload(String key, RealtimeFeed oldValue) {
                    ListenableFutureTask<RealtimeFeed> task = ListenableFutureTask.create(() -> fetchFeedsAndCreateGraph());
                    executor.execute(task);
                    return task;
                }
            });

    private RealtimeFeed fetchFeedsAndCreateGraph() {
        Map<String, GtfsRealtime.FeedMessage> feedMessageMap = new HashMap<>();
        for (RealtimeFeedConfiguration configuration : bundleConfiguration.gtfsrealtime()) {
            feedMessageMap.put(configuration.getFeedId(), configuration.getFeedMessage());
        }
        return RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, ptFlagEncoder, feedMessageMap);
    }

    @Inject
    RealtimeFeedCache(GraphHopperStorage graphHopperStorage, GtfsStorage gtfsStorage, PtFlagEncoder ptFlagEncoder, RealtimeBundleConfiguration bundleConfiguration) {
        this.graphHopperStorage = graphHopperStorage;
        this.gtfsStorage = gtfsStorage;
        this.ptFlagEncoder = ptFlagEncoder;
        this.bundleConfiguration = bundleConfiguration;
    }

    @Override
    public RealtimeFeed provide() {
        try {
            return cache.get("pups");
        } catch (ExecutionException | RuntimeException e) {
            e.printStackTrace();
            return RealtimeFeed.empty(gtfsStorage);
        }
    }

    @Override
    public void dispose(RealtimeFeed instance) {

    }

    public RealtimeFeedConfiguration getConfiguration(String feedId) {
        for (RealtimeFeedConfiguration realtimeFeedConfiguration : bundleConfiguration.gtfsrealtime()) {
            if (feedId.equals(realtimeFeedConfiguration.getFeedId())) {
                return realtimeFeedConfiguration;
            }
        }
        return null;
    }
}
