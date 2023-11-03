/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.core.model.appearance.GeoreferencedTexture;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.model.relief.AbstractReliefComponent;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.xml.reader.FeatureInfo;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.referencing.CRS;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.common.CoordinateListProvider;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.*;
import org.xmlobjects.gml.model.geometry.primitives.*;
import org.xmlobjects.gml.util.jama.Matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Reprojector {
    private final Map<String, ReferenceSystem> referenceSystems = new HashMap<>();
    private final Map<String, MathTransform> transforms = new HashMap<>();
    private final ReprojectProcessor reprojectProcessor = new ReprojectProcessor();
    private final PostProcessor postProcessor = new PostProcessor();

    private ReferenceSystem target;
    private String targetName;
    private ReferenceSystem source;
    private boolean swapAxisOrder;
    private boolean keepHeightValues;
    private boolean lenientTransform;
    private String rootSrsName;

    private Reprojector() {
    }

    public static Reprojector of(String name, boolean forceLongitudeFirst) throws ExecutionException {
        return new Reprojector().withTargetCRS(name, forceLongitudeFirst);
    }

    public ReferenceSystem getTargetCRS() {
        return target;
    }

    private Reprojector withTargetCRS(String name, boolean forceLongitudeFirst) throws ExecutionException {
        try {
            target = getOrCreateReferenceSystem(name, forceLongitudeFirst);
            if (target.getCode() == 0) {
                target.setCode(CRS.lookupEpsgCode(target.getCRS(), false));
            }

            targetName = target.toURL();
        } catch (FactoryException | ExecutionException e) {
            throw new ExecutionException("Failed to set the target CRS.", e);
        }

        return this;
    }

    public String getTargetName() {
        return targetName;
    }

    public Reprojector withTargetName(String targetName) {
        if (targetName != null) {
            this.targetName = targetName;
        }

        return this;
    }

    public Reprojector withSourceCRS(String name) throws ExecutionException {
        if (name != null) {
            try {
                source = getOrCreateReferenceSystem(name);
            } catch (ExecutionException e) {
                throw new ExecutionException("Failed to set the source CRS.", e);
            }

            getOrCreateTransform(source);
        }

        return this;
    }

    public Reprojector swapAxisOrder(boolean swapAxisOrder) {
        this.swapAxisOrder = swapAxisOrder;
        return this;
    }

    public Reprojector keepHeightValues(boolean keepHeightValues) {
        this.keepHeightValues = keepHeightValues;
        return this;
    }

    public Reprojector lenientTransform(boolean lenientTransform) {
        this.lenientTransform = lenientTransform;
        return this;
    }

    public Reprojector withCityModelInfo(FeatureInfo cityModelInfo) {
        if (cityModelInfo != null
                && cityModelInfo.getBoundedBy() != null
                && cityModelInfo.getBoundedBy().isSetEnvelope()) {
            Envelope envelope = cityModelInfo.getBoundedBy().getEnvelope();
            rootSrsName = envelope.getSrsName();
            reprojectProcessor.transformEnvelope(envelope, cityModelInfo.getBackingFeature());
        }

        return this;
    }

    public void reproject(AbstractFeature feature) {
        feature.accept(reprojectProcessor);
        feature.accept(postProcessor);

        if (rootSrsName == null
                && (feature.getBoundedBy() == null
                || !feature.getBoundedBy().isSetEnvelope())
                && feature.getGeometryInfo(true).hasGeometries()) {
            Envelope envelope = feature.computeEnvelope();
            envelope.setSrsDimension(3);
            envelope.setSrsName(targetName);
            feature.setBoundedBy(new BoundingShape(envelope));
        }
    }

    private ReferenceSystem getOrCreateReferenceSystem(String name) throws ExecutionException {
        return getOrCreateReferenceSystem(name, false);
    }

    private ReferenceSystem getOrCreateReferenceSystem(String name, boolean forceLongitudeFirst) throws ExecutionException {
        ReferenceSystem referenceSystem = referenceSystems.get(name);
        if (referenceSystem == null) {
            referenceSystem = ReferenceSystem.of(name, forceLongitudeFirst);
            referenceSystems.put(name, referenceSystem);
        }

        return referenceSystem;
    }

    private MathTransform getOrCreateTransform(ReferenceSystem referenceSystem) throws ExecutionException {
        return getOrCreateTransform(referenceSystem, referenceSystem.toShortNotation());
    }

    private MathTransform getOrCreateTransform(ReferenceSystem source, String name) throws ExecutionException {
        MathTransform transform = transforms.get(name);
        if (transform == null) {
            try {
                transform = CRS.findMathTransform(source.getCRS(), target.getCRS(), lenientTransform);
                transforms.put(name, transform);
            } catch (FactoryException e) {
                throw new ExecutionException("Failed to find a transformation from '" + name + "' to '" +
                        targetName + "'.", e);
            }
        }

        return transform;
    }

    private class ReprojectProcessor extends ObjectWalker {

        @Override
        public void visit(Point point) {
            if (point.getPos() != null) {
                transformPosition(point.getPos(), point);
            }
        }

        @Override
        public void visit(Curve curve) {
            if (curve.getSegments() != null && curve.getSegments().isSetObjects()) {
                for (AbstractCurveSegment segment : curve.getSegments().getObjects()) {
                    if (segment instanceof LineStringSegment) {
                        transformPositionList(((LineStringSegment) segment).getControlPoints(), curve);
                    }
                }
            }
        }

        @Override
        public void visit(LineString lineString) {
            if (lineString.isSetControlPoints()) {
                transformPositionList(lineString.getControlPoints(), lineString);
            }
        }

        @Override
        public void visit(LinearRing linearRing) {
            if (linearRing.isSetControlPoints()) {
                transformPositionList(linearRing.getControlPoints(), linearRing);
            }
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (implicitGeometry.getTransformationMatrix() != null) {
                Matrix matrix = implicitGeometry.getTransformationMatrix().getValue();
                List<Double> coordinates = implicitGeometry.getReferencePoint() != null
                        && implicitGeometry.getReferencePoint().getObject() != null ?
                        implicitGeometry.getReferencePoint().getObject().toCoordinateList3D() :
                        new ArrayList<>();

                coordinates.set(0, coordinates.get(0) + matrix.get(0, 3));
                coordinates.set(1, coordinates.get(1) + matrix.get(1, 3));
                coordinates.set(2, coordinates.get(2) + matrix.get(2, 3));
                matrix.set(0, 3, 0);
                matrix.set(1, 3, 0);
                matrix.set(2, 3, 0);

                DirectPosition position = new DirectPosition(coordinates);
                position.setSrsDimension(3);
                implicitGeometry.getReferencePoint().getObject().setPos(position);
            }

            if (implicitGeometry.getReferencePoint() != null
                    && implicitGeometry.getReferencePoint().getObject() != null) {
                implicitGeometry.getReferencePoint().getObject().accept(this);
            }
        }

        @Override
        public void visit(GeoreferencedTexture georeferencedTexture) {
            if (georeferencedTexture.getReferencePoint() != null
                    && georeferencedTexture.getReferencePoint().getObject() != null) {
                Point point = georeferencedTexture.getReferencePoint().getObject();
                point.accept(this);

                DirectPosition position = point.getPos();
                if (position != null) {
                    position.getValue().remove(2);
                    position.setSrsDimension(2);
                }
            }
        }

        @Override
        public void visit(AbstractReliefComponent reliefComponent) {
            if (reliefComponent.getExtent() != null && reliefComponent.getExtent().getObject() != null) {
                Polygon extent = reliefComponent.getExtent().getObject();
                extent.accept(this);
                extent.accept(new ObjectWalker() {
                    @Override
                    public void visit(LinearRing linearRing) {
                        List<Double> coordinates = linearRing.toCoordinateList3D();
                        DirectPositionList positionList = new DirectPositionList(
                                IntStream.range(0, coordinates.size())
                                        .filter(i -> (i + 1) % 3 != 0)
                                        .mapToObj(coordinates::get)
                                        .collect(Collectors.toList()));
                        positionList.setSrsDimension(2);
                        linearRing.getControlPoints().setPosList(positionList);
                    }
                });
            }
        }

        private void transformEnvelope(Envelope envelope, AbstractFeature parent) {
            List<Double> coordinates = transformCoordinates(envelope, getTransform(envelope, parent));
            envelope.setLowerCorner(new DirectPosition(coordinates.subList(0, 3)));
            envelope.setUpperCorner(new DirectPosition(coordinates.subList(3, 6)));
            envelope.setSrsName(targetName);
            envelope.setSrsDimension(3);
        }

        private void transformPositionList(GeometricPositionList positionList, AbstractGML parent) {
            if (positionList.isSetPosList()) {
                transformPositionList(positionList.getPosList(), parent);
            } else if (positionList.isSetGeometricPositions()) {
                positionList.getGeometricPositions().forEach(position -> transformPosition(position, parent));
            }
        }

        private void transformPositionList(DirectPositionList positionList, AbstractGML parent) {
            positionList.setValue(transformCoordinates(positionList, getTransform(positionList, parent)));
            positionList.setSrsName(null);
            positionList.setSrsDimension(3);
        }

        private void transformPosition(GeometricPosition position, AbstractGML parent) {
            if (position.isSetPos()) {
                transformPosition(position.getPos(), parent);
            } else if (position.isSetPointProperty() && position.getPointProperty().getObject() != null) {
                position.getPointProperty().getObject().accept(this);
            }
        }

        private void transformPosition(DirectPosition position, AbstractGML parent) {
            position.setValue(transformCoordinates(position, getTransform(position, parent)));
            position.setSrsName(null);
            position.setSrsDimension(3);
        }

        private List<Double> transformCoordinates(CoordinateListProvider provider, MathTransform transform) {
            List<Double> coordinates = provider.toCoordinateList3D();
            if (!transform.isIdentity()) {
                try {
                    double[] source = new double[transform.getSourceDimensions()];
                    double[] target = new double[transform.getTargetDimensions()];

                    for (int i = 0; i < coordinates.size(); i += 3) {
                        if (swapAxisOrder) {
                            source[0] = coordinates.get(i + 1);
                            source[1] = coordinates.get(i);
                        } else {
                            source[0] = coordinates.get(i);
                            source[1] = coordinates.get(i + 1);
                        }

                        if (source.length > 2) {
                            source[2] = coordinates.get(i + 2);
                        }

                        transform.transform(source, 0, target, 0, 1);

                        coordinates.set(i, target[0]);
                        coordinates.set(i + 1, target[1]);
                        if (target.length > 2 && !keepHeightValues) {
                            coordinates.set(i + 2, target[2]);
                        }
                    }
                } catch (TransformException e) {
                    throw new RuntimeException("Failed to transform coordinates.", e);
                }
            }

            return coordinates;
        }

        private MathTransform getTransform(SRSReference reference, AbstractGML parent) {
            try {
                if (source != null) {
                    return getOrCreateTransform(source);
                } else {
                    String srsName = getSrsName(reference, parent);
                    return getOrCreateTransform(getOrCreateReferenceSystem(srsName), srsName);
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        private String getSrsName(SRSReference reference, AbstractGML parent) {
            String srsName = reference.getInheritedSRSReference().getSrsName();
            if (srsName == null) {
                if (rootSrsName != null) {
                    return rootSrsName;
                } else {
                    String object = parent instanceof AbstractFeature ?
                            reference.getClass().getSimpleName() :
                            CityObjects.getObjectSignature(parent);
                    String feature = parent instanceof AbstractFeature ?
                            CityObjects.getObjectSignature(parent) :
                            CityObjects.getObjectSignature(parent.getParent(AbstractFeature.class));

                    throw new RuntimeException("Failed to find a CRS definition for " +
                            object + " of " + feature + ".");
                }
            } else {
                return srsName;
            }
        }
    }

    private class PostProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (feature.getBoundedBy() != null && feature.getBoundedBy().isSetEnvelope()) {
                reprojectProcessor.transformEnvelope(feature.getBoundedBy().getEnvelope(), feature);
            }

            super.visit(feature);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            geometry.setSrsName(null);
            super.visit(geometry);
        }
    }
}
