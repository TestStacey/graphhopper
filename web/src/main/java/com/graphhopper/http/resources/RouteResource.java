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

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.GHException;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.PrintWriter;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Servlet to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise. See the full API response format in docs/web/api-doc.md
 * <p>
 *
 * @author Peter Karich
 */
@Path("route")
public class RouteResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopperAPI graphHopper;
    private final EncodingManager encodingManager;
    private final Boolean hasElevation;
    private final GraphHopperStorage graph;
    private final MetricRegistry metricRegistry;

    @Inject
    public RouteResource(GraphHopperAPI graphHopper, EncodingManager encodingManager, @Named("hasElevation") Boolean hasElevation, GraphHopperStorage graph, MetricRegistry metricRegistry) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.hasElevation = hasElevation;
        this.graph = graph;
        this.metricRegistry = metricRegistry;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("point") List<GHPoint> requestPoints,
            @QueryParam("type") @DefaultValue("json") String type,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("weighting") @DefaultValue("fastest") String weighting,
            @QueryParam("algorithm") @DefaultValue("") String algoStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.Routing.POINT_HINT) List<String> pointHints,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("heading") List<Double> favoredHeadings,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("millis") String timeString) {
        boolean writeGPX = "gpx".equalsIgnoreCase(type);
        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        if(requestPoints.isEmpty()) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("You have to pass at least one point"), writeGPX));
        }

        if (!encodingManager.supports(vehicleStr)) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("Vehicle not supported: " + vehicleStr), writeGPX));
        } else if (enableElevation && !hasElevation) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("Elevation not supported!"), writeGPX));
        } else if (favoredHeadings.size() > 1 && favoredHeadings.size() != requestPoints.size()) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("The number of 'heading' parameters must be <= 1 "
                    + "or equal to the number of points (" + requestPoints.size() + ")"), writeGPX));
        }

        if (pointHints.size() > 0 && pointHints.size() != requestPoints.size()) {
            throw new WebApplicationException(errorResponse(new IllegalArgumentException("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored"), writeGPX));
        }

        GHRequest request;
        if (favoredHeadings.size() > 0) {
            // if only one favored heading is specified take as start heading
            if (favoredHeadings.size() == 1) {
                List<Double> paddedHeadings = new ArrayList<>(Collections.nCopies(requestPoints.size(), Double.NaN));
                paddedHeadings.set(0, favoredHeadings.get(0));
                request = new GHRequest(requestPoints, paddedHeadings);
            } else {
                request = new GHRequest(requestPoints, favoredHeadings);
            }
        } else {
            request = new GHRequest(requestPoints);
        }

        initHints(request.getHints(), uriInfo.getQueryParameters());
        request.setVehicle(encodingManager.getEncoder(vehicleStr).toString()).
                setWeighting(weighting).
                setAlgorithm(algoStr).
                setLocale(localeStr).
                setPointHints(pointHints).
                setPathDetails(pathDetails).
                getHints().
                put(CALC_POINTS, calcPoints).
                put(INSTRUCTIONS, instructions).
                put(WAY_POINT_MAX_DISTANCE, minPathPrecision);

        if (type.equals("stream")) {
            return Response.ok((StreamingOutput) output -> {
                final PrintWriter printWriter = new PrintWriter(output);
                printWriter.println("lng,lat");
                ((GraphHopperGtfs) graphHopper).routeStreaming(request, l -> {
                    if (l.adjNode <= graph.getNodes()) {
                        printWriter.printf(
                                "%f,%f\n",
                                graph.getNodeAccess().getLon(l.adjNode),
                                graph.getNodeAccess().getLat(l.adjNode));
                    }
                });
                printWriter.close();
            }).build();
        } else if (type.equals("graph")) {
            return Response.ok((StreamingOutput) output -> {
                final PtFlagEncoder flagEncoder = (PtFlagEncoder) encodingManager.getEncoder("pt");
                final PrintWriter printWriter = new PrintWriter(output);
                printWriter.println("source,target,edgetype,walktime");
                ((GraphHopperGtfs) graphHopper).routeStreaming(request, l -> {
                    if (l.parent != null) {
                        String edgeType;
                        try {
                            edgeType = flagEncoder.getEdgeType(graph.getEdgeIteratorState(l.edge, l.adjNode).getFlags()).toString();
                        } catch (IllegalStateException e) {
                            edgeType = "HIGHWAY";
                        }
                        printWriter.printf(
                                "%d,%d,%s,%d\n", l.parent.adjNode, l.adjNode, edgeType, l.walkTime);
                        metricRegistry.meter(edgeType).mark();
                    }
                });
                printWriter.close();
            }).build();
        } else {
            GHResponse ghResponse = graphHopper.route(request);

            // TODO: Request logging and timing should perhaps be done somewhere outside
            float took = sw.stop().getSeconds();
            String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
            String logStr = httpReq.getQueryString() + " " + infoStr + " " + requestPoints + ", took:"
                    + took + ", " + algoStr + ", " + weighting + ", " + vehicleStr;

            if (ghResponse.hasErrors()) {
                logger.error(logStr + ", errors:" + ghResponse.getErrors());
                throw new WebApplicationException(errorResponse(ghResponse.getErrors(), writeGPX));
            } else {
                logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                        + ", distance0: " + ghResponse.getBest().getDistance()
                        + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                        + ", points0: " + ghResponse.getBest().getPoints().getSize()
                        + ", debugInfo: " + ghResponse.getDebugInfo());
                return Response.fromResponse(writeGPX ?
                        gpxSuccessResponse(ghResponse, timeString, trackName, enableElevation, withRoute, withTrack, withWayPoints) :
                        Response.ok(ghResponse).build())
                        .header("X-GH-Took", "" + Math.round(took * 1000))
                        .build();
            }
        }
    }

    private Response gpxSuccessResponse(GHResponse ghRsp, String timeString, String trackName, boolean enableElevation, boolean withRoute, boolean withTrack, boolean withWayPoints) {
        if (ghRsp.getAll().size() > 1) {
            throw new WebApplicationException("Alternatives are currently not yet supported for GPX");
        }

        long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
        return Response.ok(ghRsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints), "application/gpx+xml").header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx").build();
    }

    private Response xmlErrorResponse(Collection<Throwable> list) {
        if (list.isEmpty())
            throw new RuntimeException("errorsToXML should not be called with an empty list");

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element gpxElement = doc.createElement("gpx");
            gpxElement.setAttribute("creator", "GraphHopper");
            gpxElement.setAttribute("version", "1.1");
            doc.appendChild(gpxElement);

            Element mdElement = doc.createElement("metadata");
            gpxElement.appendChild(mdElement);

            Element extensionsElement = doc.createElement("extensions");
            mdElement.appendChild(extensionsElement);

            Element messageElement = doc.createElement("message");
            extensionsElement.appendChild(messageElement);
            messageElement.setTextContent(list.iterator().next().getMessage());

            Element hintsElement = doc.createElement("hints");
            extensionsElement.appendChild(hintsElement);

            for (Throwable t : list) {
                Element error = doc.createElement("error");
                hintsElement.appendChild(error);
                error.setAttribute("message", t.getMessage());
                error.setAttribute("details", t.getClass().getName());
            }
            return Response.status(SC_BAD_REQUEST).entity(doc).build();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
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

    private Response errorResponse(List<Throwable> t, boolean writeGPX) {
        if (writeGPX) {
            return xmlErrorResponse(t);
        } else {
            return jsonErrorResponse(t);
        }
    }

    private Response errorResponse(Throwable t, boolean writeGPX) {
        return errorResponse(Collections.singletonList(t), writeGPX);
    }

    private Response jsonErrorResponse(List<Throwable> errors) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", getMessage(errors.get(0)));
        ArrayNode errorHintList = json.putArray("hints");
        for (Throwable t : errors) {
            ObjectNode error = errorHintList.addObject();
            error.put("message", getMessage(t));
            error.put("details", t.getClass().getName());
            if (t instanceof GHException) {
                ((GHException) t).getDetails().forEach(error::putPOJO);
            }
        }
        return Response.serverError().entity(json).build();
    }

    private String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }


}
