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

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.AbstractSpaceBoundary;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.model.relief.AbstractReliefComponent;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.common.CoordinateListProvider;
import org.xmlobjects.gml.model.geometry.*;
import org.xmlobjects.gml.model.geometry.primitives.*;

import java.util.List;
import java.util.Map;

public class HeightChanger {
    private final double offset;
    private final HeightProcessor heightProcessor = new HeightProcessor();
    private final ImplicitGeometryResolver resolver = new ImplicitGeometryResolver();

    private Map<String, AbstractGeometry> templates;
    private Mode mode = Mode.RELATIVE;
    private double correction;

    public enum Mode {
        RELATIVE,
        ABSOLUTE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private HeightChanger(double offset) {
        this.offset = offset;
    }

    public static HeightChanger of(double offset) {
        return new HeightChanger(offset);
    }

    public HeightChanger withTemplateGeometries(Map<String, AbstractGeometry> templates) {
        this.templates = templates;
        return this;
    }

    public HeightChanger withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public void changeHeight(AbstractFeature feature) {
        if (offset != 0 || mode != Mode.RELATIVE) {
            if (templates != null && !templates.isEmpty()) {
                feature.accept(resolver);
            }

            double minimumHeight = mode == Mode.ABSOLUTE ?
                    feature.computeEnvelope().getLowerCorner().getValue().get(2) :
                    0;

            correction = offset - minimumHeight;
            if (correction != 0) {
                feature.accept(heightProcessor);
            }
        }
    }

    private class HeightProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (feature.getBoundedBy() != null && feature.getBoundedBy().isSetEnvelope()) {
                Envelope envelope = feature.getBoundedBy().getEnvelope();
                List<Double> coordinates = changeCoordinates(envelope);
                envelope.getLowerCorner().setValue(coordinates.subList(0, 3));
                envelope.getUpperCorner().setValue(coordinates.subList(3, 6));
            }

            super.visit(feature);
        }

        @Override
        public void visit(Point point) {
            point.setPos(changePosition(point));
        }

        @Override
        public void visit(Curve curve) {
            if (curve.getSegments() != null && curve.getSegments().isSetObjects()) {
                for (AbstractCurveSegment segment : curve.getSegments().getObjects()) {
                    if (segment instanceof LineStringSegment) {
                        LineStringSegment lineString = (LineStringSegment) segment;
                        lineString.getControlPoints().setPosList(changePositionList(lineString));
                    }
                }
            }
        }

        @Override
        public void visit(LineString lineString) {
            lineString.getControlPoints().setPosList(changePositionList(lineString));
        }

        @Override
        public void visit(LinearRing linearRing) {
            linearRing.getControlPoints().setPosList(changePositionList(linearRing));
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (implicitGeometry.getReferencePoint() != null
                    && implicitGeometry.getReferencePoint().getObject() != null) {
                visit(implicitGeometry.getReferencePoint().getObject());
            }
        }

        @Override
        public void visit(AbstractReliefComponent reliefComponent) {
            visit((AbstractSpaceBoundary) reliefComponent);
        }

        @Override
        public void visit(AbstractInlineOrByReferenceProperty<?> property) {
            if (property.isSetInlineObject()) {
                super.visit(property);
            }
        }

        @Override
        public void visit(Appearance appearance) {
        }

        private DirectPosition changePosition(CoordinateListProvider provider) {
            DirectPosition position = new DirectPosition(changeCoordinates(provider));
            position.setSrsDimension(3);
            return position;
        }

        private DirectPositionList changePositionList(CoordinateListProvider provider) {
            DirectPositionList positionList = new DirectPositionList(changeCoordinates(provider));
            positionList.setSrsDimension(3);
            return positionList;
        }

        private List<Double> changeCoordinates(CoordinateListProvider provider) {
            List<Double> coordinates = provider.toCoordinateList3D();
            for (int i = 2; i < coordinates.size(); i += 3) {
                coordinates.set(i, coordinates.get(i) + correction);
            }

            return coordinates;
        }
    }

    private class ImplicitGeometryResolver extends ObjectWalker {

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
            if (property != null
                    && property.getObject() == null
                    && property.getHref() != null) {
                AbstractGeometry template = templates.get(CityObjects.getIdFromReference(property.getHref()));
                if (template != null) {
                    property.setReferencedObjectIfValid(template);
                }
            }
        }
    }
}
