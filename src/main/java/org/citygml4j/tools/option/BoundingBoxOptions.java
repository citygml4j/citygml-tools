/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citygml4j.tools.option;

import org.citygml4j.tools.util.BoundingBoxFilter;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.Envelope;
import picocli.CommandLine;

public class BoundingBoxOptions implements Option {
    @CommandLine.Option(names = {"-b", "--bbox"}, paramLabel = "<minx,miny,maxx,maxy>",
            required = true, description = "Bounding box to use as spatial filter. The reference system of the " +
            "coordinates must match the reference system of the input file(s).")
    private String bbox;

    @CommandLine.Option(names = "--bbox-mode", paramLabel = "<mode>", defaultValue = "intersects",
            description = "Bounding box filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private BoundingBoxFilter.Mode mode;

    private Envelope boundingBox;

    public Envelope getBoundingBox() {
        return boundingBox;
    }

    public BoundingBoxFilter.Mode getMode() {
        return mode;
    }

    public BoundingBoxFilter toBoundingBoxFilter() {
        return boundingBox != null ?
                BoundingBoxFilter.of(boundingBox).withMode(mode) :
                null;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (bbox != null) {
            String[] parts = bbox.split(",");
            if (parts.length == 4) {
                try {
                    boundingBox = new Envelope(
                            new DirectPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])),
                            new DirectPosition(Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                } catch (NumberFormatException e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The coordinates of a bounding box must be floating point numbers but were '" +
                                    String.join(",", parts[0], parts[1], parts[2], parts[3]) + "'");
                }

                if (!boundingBox.isValid()) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The specified bounding box '" + bbox + "' is not valid.");
                }
            } else {
                throw new CommandLine.ParameterException(commandLine,
                        "A bounding box must be in MINX,MINY,MAXX,MAXY format but was '" + bbox + "'");
            }
        }
    }
}
