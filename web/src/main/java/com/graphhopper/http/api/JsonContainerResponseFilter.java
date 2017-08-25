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
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.jaxrs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterModifier;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;

import static java.util.Collections.singletonList;

public class JsonContainerResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        ObjectWriterInjector.set(new ObjectWriterModifier() {
            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, Object> responseHeaders, Object valueToWrite, ObjectWriter w, JsonGenerator g) throws IOException {
                //TODO I can't use jax-ws annotations here, but there's got to be a better way than manual parsing
                // and duplicating the defaults between here and the Resource.
                final boolean calcPoints = Boolean.parseBoolean(requestContext.getUriInfo().getQueryParameters().getOrDefault("calcPoints", singletonList("true")).get(0));
                final boolean pointsEncoded = Boolean.parseBoolean(requestContext.getUriInfo().getQueryParameters().getOrDefault("points_encoded", singletonList("true")).get(0));
                final boolean elevation = Boolean.parseBoolean(requestContext.getUriInfo().getQueryParameters().getOrDefault("elevation", singletonList("false")).get(0));
                final boolean instructions = Boolean.parseBoolean(requestContext.getUriInfo().getQueryParameters().getOrDefault("instructions", singletonList("true")).get(0));
            return w.with(w.getAttributes()
                    .withSharedAttribute(GHResponseSerializer.Attributes.class, new GHResponseSerializer.Attributes(
                            calcPoints,
                            pointsEncoded,
                            elevation,
                            instructions)));
            }
        });
    }
}
