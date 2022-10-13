/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.util;

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.xml.reader.FeatureInfo;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BoundingBoxFilter {
    private final Logger log = Logger.getInstance();
    private final ReferenceSystemValidator referenceSystemValidator = new ReferenceSystemValidator();
    private final Envelope boundingBox;
    private Mode mode = Mode.INTERSECTS;
    private String rootReferenceSystem;

    public enum Mode {
        INTERSECTS,
        WITHIN;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private BoundingBoxFilter(Envelope boundingBox) {
        this.boundingBox = boundingBox;
    }

    public static BoundingBoxFilter of(Envelope boundingBox) {
        return new BoundingBoxFilter(boundingBox);
    }

    public BoundingBoxFilter withMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.INTERSECTS;
        return this;
    }

    public BoundingBoxFilter withRootReferenceSystem(FeatureInfo cityModelInfo) {
        if (cityModelInfo != null
                && cityModelInfo.getBoundedBy() != null
                && cityModelInfo.getBoundedBy().isSetEnvelope()) {
            rootReferenceSystem = cityModelInfo.getBoundedBy().getEnvelope().getSrsName();
        }

        return this;
    }

    public boolean filter(AbstractFeature feature) {
        if (!referenceSystemValidator.validate(feature, rootReferenceSystem)) {
            log.error(CityObjects.getObjectSignature(feature) + ": Cannot apply bounding box filter because the " +
                    "city object uses multiple reference systems.");
            return false;
        }

        Envelope candidate = feature.computeEnvelope();
        if (!candidate.isEmpty() && candidate.isValid()) {
            return mode == Mode.WITHIN ?
                    boundingBox.contains(candidate):
                    boundingBox.intersects(candidate);
        }

        return false;
    }

    private static class ReferenceSystemValidator extends ObjectWalker {
        private final Set<String> referenceSystems = new HashSet<>();

        public boolean validate(AbstractFeature feature, String rootReferenceSystem) {
            try {
                if (rootReferenceSystem != null) {
                    referenceSystems.add(rootReferenceSystem);
                }

                feature.accept(this);
                return referenceSystems.size() <= 1;
            } finally {
                referenceSystems.clear();
            }
        }

        @Override
        public void visit(AbstractFeature feature) {
            if (feature.getBoundedBy() != null
                    && feature.getBoundedBy().isSetEnvelope()
                    && feature.getBoundedBy().getEnvelope().getSrsName() != null) {
                referenceSystems.add(feature.getBoundedBy().getEnvelope().getSrsName());
            }

            super.visit(feature);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getSrsName() != null) {
                referenceSystems.add(geometry.getSrsName());
            }

            super.visit(geometry);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (implicitGeometry.getReferencePoint() != null
                    && implicitGeometry.getReferencePoint().getObject() != null) {
                implicitGeometry.getRelativeGeometry().getObject().accept(this);
            }
        }
    }
}
