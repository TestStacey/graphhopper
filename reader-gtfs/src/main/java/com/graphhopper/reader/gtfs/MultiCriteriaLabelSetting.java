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
package com.graphhopper.reader.gtfs;

import com.google.common.collect.ArrayListMultimap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 */
public class MultiCriteriaLabelSetting {

    private final Comparator<Label> queueComparator;
    private long startTime;
    private final PtFlagEncoder flagEncoder;
    private final PtTravelTimeWeighting weighting;
    private final ArrayListMultimap<Integer, Label> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final int maxVisitedNodes;
    private final boolean reverse;
    private final double maxWalkDistancePerLeg;
    private final double maxTransferDistancePerLeg;
    private final boolean mindTransfers;
    private final boolean profileQuery;
    private int visitedNodes;
    private final GraphExplorer explorer;

    MultiCriteriaLabelSetting(GraphExplorer explorer, Weighting weighting, boolean reverse, double maxWalkDistancePerLeg, double maxTransferDistancePerLeg, boolean mindTransfers, boolean profileQuery, int maxVisitedNodes) {
        this.weighting = (PtTravelTimeWeighting) weighting;
        this.flagEncoder = (PtFlagEncoder) weighting.getFlagEncoder();
        this.maxVisitedNodes = maxVisitedNodes;
        this.explorer = explorer;
        this.reverse = reverse;
        this.maxWalkDistancePerLeg = maxWalkDistancePerLeg;
        this.maxTransferDistancePerLeg = maxTransferDistancePerLeg;
        this.mindTransfers = mindTransfers;
        this.profileQuery = profileQuery;

        queueComparator = Comparator.<Label>comparingLong(l2 -> currentTimeCriterion(l2))
                .thenComparing(Comparator.comparingLong(l1 -> l1.nTransfers))
                .thenComparing(Comparator.comparingLong(l1 -> l1.walkTime))
                .thenComparing(Comparator.comparingLong(l -> departureTimeCriterion(l) != null ? departureTimeCriterion(l) : 0));
        fromHeap = new PriorityQueue<>(queueComparator);
        fromMap = ArrayListMultimap.create();
    }

    Stream<Label> calcLabels(int from, int to, Instant startTime) {
        this.startTime = startTime.toEpochMilli();
        return StreamSupport.stream(new MultiCriteriaLabelSettingSpliterator(from, to), false)
                .limit(maxVisitedNodes)
                .peek(label -> visitedNodes++);
    }

    private class MultiCriteriaLabelSettingSpliterator extends Spliterators.AbstractSpliterator<Label> {

        private final int to;
        private final Set<Label> targetLabels;

        MultiCriteriaLabelSettingSpliterator(int from, int to) {
            super(0, 0);
            this.to = to;
            targetLabels = new HashSet<>();
            Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, 0, 0.0, null, 0, null);
            fromMap.put(from, label);
            fromHeap.add(label);
            if (to == from) {
                targetLabels.add(label);
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super Label> action) {
            for(Label label = fromHeap.poll(); label != null; label = fromHeap.poll()) {
                if (label.deleted) continue;
                if (label.adjNode == 457243) {
                    System.out.println(label);
                }
//                if (label.adjNode == 449111) {
//                    System.out.println(label);
//                }
//                if (label.adjNode == 448958) {
//                    System.out.println(label);
//                }
                if (label.adjNode == 449953) {
                    System.out.println(label);
                }
                if (label.adjNode == 449891) {
                    System.out.println(label);
                }

                action.accept(label);
                Label finalLabel = label;
                explorer.exploreEdgesAround(label).forEach(edge -> {
                    GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
                    long nextTime;
                    if (reverse) {
                        nextTime = finalLabel.currentTime - explorer.calcTravelTimeMillis(edge, finalLabel.currentTime);
                    } else {
                        nextTime = finalLabel.currentTime + explorer.calcTravelTimeMillis(edge, finalLabel.currentTime);
                    }
                    int nTransfers = finalLabel.nTransfers + weighting.calcNTransfers(edge);
                    Long firstPtDepartureTime = finalLabel.departureTime;
                    if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT)) {
                        if (finalLabel.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - finalLabel.walkTime;
                        }
                    } else if (reverse && (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL)) {
                        if (finalLabel.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - finalLabel.walkTime;
                        }
                    }
                    double walkDistanceOnCurrentLeg = (!reverse && edgeType == GtfsStorage.EdgeType.BOARD || reverse && edgeType == GtfsStorage.EdgeType.ALIGHT) ? 0 : (finalLabel.walkDistanceOnCurrentLeg + weighting.getWalkDistance(edge));
                    boolean isTryingToReEnterPtAfterTransferWalking = (!reverse && edgeType == GtfsStorage.EdgeType.ENTER_PT || reverse && edgeType == GtfsStorage.EdgeType.EXIT_PT) && finalLabel.nTransfers > 0 && finalLabel.walkDistanceOnCurrentLeg > maxTransferDistancePerLeg;
                    boolean isTryingToWalkAfterRiding = (!reverse && edgeType == GtfsStorage.EdgeType.HIGHWAY || reverse && edgeType == GtfsStorage.EdgeType.HIGHWAY) && finalLabel.nTransfers > 0;
                    long walkTime = finalLabel.walkTime + (edgeType == GtfsStorage.EdgeType.HIGHWAY ? nextTime - finalLabel.currentTime : 0);
                    int nWalkDistanceConstraintViolations = Math.min(1, finalLabel.nWalkDistanceConstraintViolations + (
                            isTryingToReEnterPtAfterTransferWalking ? 1 : (finalLabel.walkDistanceOnCurrentLeg <= maxWalkDistancePerLeg && walkDistanceOnCurrentLeg > maxWalkDistancePerLeg ? 1 : 0)));
                    if (isTryingToWalkAfterRiding) {
                        nWalkDistanceConstraintViolations++;
                    }
                    Collection<Label> sptEntries = fromMap.get(edge.getAdjNode());
                    Label nEdge = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, nWalkDistanceConstraintViolations, walkDistanceOnCurrentLeg, firstPtDepartureTime, walkTime, finalLabel);
                    if (isNotDominatedByAnyOf(nEdge, sptEntries)) {
                        if (finalLabel.adjNode == 457243) {
                            System.out.println(" --> " + nEdge);
                        }
                        removeDominated(nEdge, sptEntries);
                        if (to == edge.getAdjNode()) {
                            removeDominated(nEdge, targetLabels);
                        }
                        fromMap.put(edge.getAdjNode(), nEdge);
                        if (to == edge.getAdjNode()) {
                            targetLabels.add(nEdge);
                        }
                        fromHeap.add(nEdge);
                    }
                });
                label.deleted = true;
                return true;
            }
            return false;
        }
    }

    private boolean isNotDominatedByAnyOf(Label me, Collection<Label> sptEntries) {
        if (me.nWalkDistanceConstraintViolations > 0) {
            return false;
        }
        for (Label they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }


    private void removeDominated(Label me, Collection<Label> sptEntries) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext();) {
            Label sptEntry = iterator.next();
            iterator.remove();
            sptEntry.deleted = true;
        }
    }

    private boolean dominates(Label me, Label they) {
        if (profileQuery) {
            if (me.departureTime != null && they.departureTime != null) {
                if (currentTimeCriterion(me) > currentTimeCriterion(they))
                    return false;
                if (departureTimeCriterion(me) > departureTimeCriterion(they))
                    return false;
            } else {
                if (travelTimeCriterion(me) > travelTimeCriterion(they))
                    return false;
            }
        } else {
            if (currentTimeCriterion(me) > currentTimeCriterion(they))
                return false;
        }

        if (mindTransfers && me.nTransfers > they.nTransfers)
            return false;
        if (me.nWalkDistanceConstraintViolations  > they.nWalkDistanceConstraintViolations)
            return false;

        if (profileQuery) {
            if (me.departureTime != null && they.departureTime != null) {
                if (currentTimeCriterion(me) < currentTimeCriterion(they))
                    return true;
                if (departureTimeCriterion(me) < departureTimeCriterion(they))
                    return true;
            } else {
                if (travelTimeCriterion(me) < travelTimeCriterion(they))
                    return true;
            }
        } else {
            if (currentTimeCriterion(me) < currentTimeCriterion(they))
                return true;
        }
        if (mindTransfers && me.nTransfers  < they.nTransfers)
            return true;
        if (me.nWalkDistanceConstraintViolations < they.nWalkDistanceConstraintViolations)
            return true;

        return queueComparator.compare(me,they) <= 0;
    }

    private Long departureTimeCriterion(Label label) {
        return label.departureTime == null ? null : reverse ? label.departureTime : -label.departureTime;
    }

    private long currentTimeCriterion(Label label) {
        return reverse ? -label.currentTime : label.currentTime;
    }

    private long travelTimeCriterion(Label label) {
        if (label.departureTime == null) {
            return label.walkTime;
        } else {
            return (reverse ? -1 : 1) * (label.currentTime - label.departureTime);
        }
    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
