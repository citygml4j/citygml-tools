/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2026 Claus Nagel <claus.nagel@gmail.com>
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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MultiBoundingBoxOptions implements Option {
    @CommandLine.Option(names = {"-b", "--bbox"}, paramLabel = "<[name=]minx,miny,maxx,maxy>",
            description = "Bounding box to use as spatial filter. Optionally specify a name. " +
                    "Can be specified multiple times to create multiple subsets in one pass.")
    private List<String> bboxes;

    @CommandLine.Option(names = "--bbox-file", paramLabel = "<file>",
            description = "CSV file containing multiple bounding boxes (columns: name,minx,miny,maxx,maxy).")
    private Path bboxFile;

    @CommandLine.Option(names = "--bbox-mode", paramLabel = "<mode>", defaultValue = "intersects",
            description = "Bounding box mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private BoundingBoxFilter.Mode mode;

    @CommandLine.Option(names = "--bbox-overlap", paramLabel = "<strategy>", defaultValue = "duplicate",
            description = "Strategy for features matching multiple bounding boxes: duplicate, first (default: ${DEFAULT-VALUE}).")
    private OverlapStrategy overlapStrategy;

    private List<BBoxConfig> bboxConfigs;

    public enum OverlapStrategy {
        DUPLICATE,  // Write feature to all matching bounding boxes
        FIRST       // Write feature only to the first matching bounding box
    }

    public static class BBoxConfig {
        private final String name;
        private final Envelope envelope;
        private final BoundingBoxFilter filter;

        public BBoxConfig(String name, Envelope envelope, BoundingBoxFilter.Mode mode) {
            this.name = name;
            this.envelope = envelope;
            this.filter = BoundingBoxFilter.of(envelope).withMode(mode);
        }

        public String getName() {
            return name;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public BoundingBoxFilter getFilter() {
            return filter;
        }
    }

    public List<BBoxConfig> getBBoxConfigs() {
        return bboxConfigs;
    }

    public boolean hasMultipleBBoxes() {
        return bboxConfigs != null && bboxConfigs.size() > 1;
    }

    public BoundingBoxFilter.Mode getMode() {
        return mode;
    }

    public OverlapStrategy getOverlapStrategy() {
        return overlapStrategy;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws CommandLine.ParameterException {
        bboxConfigs = new ArrayList<>();

        if (bboxFile != null && bboxes != null && !bboxes.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Cannot use both --bbox and --bbox-file options simultaneously.");
        }

        if (bboxFile != null) {
            loadBBoxesFromFile(commandLine);
        } else if (bboxes != null && !bboxes.isEmpty()) {
            loadBBoxesFromCommandLine(commandLine);
        }

        if (bboxConfigs.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: At least one bounding box must be specified using --bbox or --bbox-file.");
        }
    }

    private void loadBBoxesFromCommandLine(CommandLine commandLine) throws CommandLine.ParameterException {
        int autoNumber = 1;
        for (String bbox : bboxes) {
            String name = null;
            String coords = bbox;

            // Check if bbox has format "name=minx,miny,maxx,maxy"
            if (bbox.contains("=")) {
                int equalsIndex = bbox.indexOf('=');
                name = bbox.substring(0, equalsIndex).trim();
                coords = bbox.substring(equalsIndex + 1).trim();

                if (name.isEmpty()) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: Bounding box name cannot be empty: '" + bbox + "'");
                }

                // Validate name (only alphanumeric, dash, underscore)
                if (!name.matches("[a-zA-Z0-9_-]+")) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: Bounding box name must contain only letters, numbers, dash, or underscore: '" + name + "'");
                }
            } else {
                name = String.valueOf(autoNumber++);
            }

            Envelope envelope = parseBBoxCoordinates(coords, commandLine);
            bboxConfigs.add(new BBoxConfig(name, envelope, mode));
        }
    }

    private void loadBBoxesFromFile(CommandLine commandLine) throws CommandLine.ParameterException {
        if (!Files.exists(bboxFile)) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Bounding box file does not exist: " + bboxFile);
        }

        try (BufferedReader reader = Files.newBufferedReader(bboxFile)) {
            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Skip header line (if it contains "name" and "min")
                if (!headerSkipped) {
                    if (line.toLowerCase().contains("name") && line.toLowerCase().contains("min")) {
                        headerSkipped = true;
                        continue;
                    }
                    headerSkipped = true;
                }

                String[] parts = line.split(",");
                if (parts.length != 5) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: Invalid format in bounding box file at line " + lineNumber +
                                    ". Expected format: name,minx,miny,maxx,maxy");
                }

                String name = parts[0].trim();
                if (name.isEmpty()) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: Bounding box name cannot be empty at line " + lineNumber);
                }

                String coords = String.join(",", parts[1], parts[2], parts[3], parts[4]);
                Envelope envelope = parseBBoxCoordinates(coords, commandLine);
                bboxConfigs.add(new BBoxConfig(name, envelope, mode));
            }
        } catch (IOException e) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Failed to read bounding box file: " + e.getMessage(), e);
        }

        if (bboxConfigs.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: No valid bounding boxes found in file: " + bboxFile);
        }
    }

    private Envelope parseBBoxCoordinates(String coords, CommandLine commandLine) throws CommandLine.ParameterException {
        String[] parts = coords.split(",");
        if (parts.length != 4) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: A bounding box must be in minx,miny,maxx,maxy format but was '" + coords + "'");
        }

        try {
            double minx = Double.parseDouble(parts[0].trim());
            double miny = Double.parseDouble(parts[1].trim());
            double maxx = Double.parseDouble(parts[2].trim());
            double maxy = Double.parseDouble(parts[3].trim());

            Envelope envelope = new Envelope(
                    new DirectPosition(minx, miny),
                    new DirectPosition(maxx, maxy));

            if (!envelope.isValid()) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The specified bounding box '" + coords + "' is not valid.");
            }

            return envelope;
        } catch (NumberFormatException e) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The coordinates of a bounding box must be floating point numbers but were '" + coords + "'");
        }
    }
}
