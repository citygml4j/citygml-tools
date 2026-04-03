/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.tools.option.Option;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.Envelope;
import picocli.CommandLine;

public class BoundingBoxOptions implements Option {
    @CommandLine.Option(names = {"-b", "--bbox"}, paramLabel = "<minx,miny,maxx,maxy>", required = true,
            description = "Bounding box to use as spatial filter. The reference system of the " +
                    "coordinates must match the reference system of the input files.")
    private String bbox;

    @CommandLine.Option(names = "--bbox-mode", paramLabel = "<mode>", defaultValue = "intersects",
            description = "Bounding box mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
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
