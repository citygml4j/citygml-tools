package org.citygml4j.tools.heightchanger;

import org.citygml4j.geometry.BoundingBox;
import org.citygml4j.geometry.Matrix;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.model.gml.feature.BoundingShape;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.GeometryProperty;
import org.citygml4j.model.gml.geometry.primitives.AbstractCurveSegment;
import org.citygml4j.model.gml.geometry.primitives.Curve;
import org.citygml4j.model.gml.geometry.primitives.CurveSegmentArrayProperty;
import org.citygml4j.model.gml.geometry.primitives.DirectPosition;
import org.citygml4j.model.gml.geometry.primitives.DirectPositionList;
import org.citygml4j.model.gml.geometry.primitives.LineString;
import org.citygml4j.model.gml.geometry.primitives.LineStringSegment;
import org.citygml4j.model.gml.geometry.primitives.LinearRing;
import org.citygml4j.model.gml.geometry.primitives.Point;
import org.citygml4j.util.bbox.BoundingBoxOptions;
import org.citygml4j.util.walker.GMLWalker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeightChanger {
    private final ChangeHeightWalker walker = new ChangeHeightWalker();
    private HeightMode heightMode = HeightMode.RELATIVE;
    private Map<String, BoundingBox> implicitGeometries;

    private HeightChanger() {
        // just to thwart instantiation
    }

    public static HeightChanger defaults() {
        return new HeightChanger();
    }

    public HeightChanger withHeightMode(HeightMode heightMode) {
        this.heightMode = heightMode;
        return this;
    }

    public HeightChanger withImplicitGeometries(List<ImplicitGeometry> implicitGeometries) {
        this.implicitGeometries = new HashMap<>();

        for (ImplicitGeometry implicitGeometry : implicitGeometries) {
            if (implicitGeometry.isSetRelativeGMLGeometry()
                    && implicitGeometry.getRelativeGMLGeometry().isSetGeometry()
                    && implicitGeometry.getRelativeGMLGeometry().getGeometry().isSetId()) {
                AbstractGeometry geometry = implicitGeometry.getRelativeGMLGeometry().getGeometry();
                BoundingBox bbox = geometry.calcBoundingBox();
                if (!bbox.isNull())
                    this.implicitGeometries.put(geometry.getId(), bbox);
            }
        }

        return this;
    }

    public void changeHeight(AbstractFeature feature, double offset) throws ChangeHeightException {
        if (heightMode == HeightMode.RELATIVE && offset == 0)
            return;

        // calculate minz value
        double minz = 0;
        if (heightMode == HeightMode.ABSOLUTE) {
            minz = calculateMinZ(feature);
            if (minz == Double.POSITIVE_INFINITY)
                throw new ChangeHeightException("Failed to calculate the minimum bounding box.");
        }

        // calculate height correction
        double correction = offset - minz;
        if (correction == 0)
            return;

        // apply height correction value
        walker.correction = correction;
        feature.accept(walker);
    }

    private double calculateMinZ(AbstractFeature feature) {
        final double[] minz = {Double.POSITIVE_INFINITY};

        // calculate the bounding box of the city object
        // this also considers implicit geometries given inline
        BoundingBoxOptions options = BoundingBoxOptions.defaults()
                .assignResultToFeatures(false)
                .useExistingEnvelopes(false);

        BoundingShape boundingShape = feature.calcBoundedBy(options);
        if (boundingShape != null && boundingShape.isSetEnvelope())
            minz[0] = boundingShape.getEnvelope().toBoundingBox().getLowerCorner().getZ();

        // in case the city object only refers to an implicit geometry per xlink,
        // we need to check the referenced implicit geometries as well
        if (implicitGeometries != null && !implicitGeometries.isEmpty()) {
            feature.accept(new GMLWalker() {
                public void visit(ImplicitGeometry implicitGeometry) {
                    GeometryProperty<? extends AbstractGeometry> property = implicitGeometry.getRelativeGMLGeometry();
                    if (property != null && property.isSetHref() && !property.isSetGeometry()) {
                        BoundingBox bbox = implicitGeometries.get(property.getHref().replaceAll("^#", ""));

                        if (bbox != null
                                && implicitGeometry.isSetTransformationMatrix()
                                && implicitGeometry.isSetReferencePoint()
                                && implicitGeometry.getReferencePoint().isSetPoint()) {
                            Matrix m = implicitGeometry.getTransformationMatrix().getMatrix().copy();
                            List<Double> point = implicitGeometry.getReferencePoint().getPoint().toList3d();
                            m.set(0, 3, m.get(0, 3) + point.get(0));
                            m.set(1, 3, m.get(1, 3) + point.get(1));
                            m.set(2, 3, m.get(2, 3) + point.get(2));

                            BoundingBox transformed = new BoundingBox(bbox);
                            transformed.transform3d(m);

                            if (transformed.getLowerCorner().getZ() < minz[0])
                                minz[0] = transformed.getLowerCorner().getZ();
                        }
                    }
                }
            });
        }

        return minz[0];
    }

    private final class ChangeHeightWalker extends GMLWalker {
        private double correction;

        @Override
        public void visit(LinearRing linearRing) {
            List<Double> coords = linearRing.toList3d();
            linearRing.unsetPosOrPointPropertyOrPointRep();
            linearRing.unsetCoord();
            linearRing.unsetCoordinates();
            linearRing.setPosList(adaptPositionList(coords));
        }

        @Override
        public void visit(LineString lineString) {
            List<Double> coords = lineString.toList3d();
            lineString.unsetPosOrPointPropertyOrPointRepOrCoord();
            lineString.unsetCoordinates();
            lineString.setPosList(adaptPositionList(coords));
        }

        @Override
        public void visit(Curve curve) {
            CurveSegmentArrayProperty property = curve.getSegments();
            for (AbstractCurveSegment segment : property.getCurveSegment()) {
                if (segment instanceof LineStringSegment) {
                    LineStringSegment lineString = (LineStringSegment)segment;
                    List<Double> coords = lineString.toList3d();
                    lineString.unsetPosOrPointPropertyOrPointRep();
                    lineString.unsetCoordinates();
                    lineString.setPosList(adaptPositionList(coords));
                }
            }
        }

        @Override
        public void visit(Point point) {
            List<Double> coords = point.toList3d();
            point.unsetCoord();
            point.unsetCoordinates();
            point.setPos(adaptPosition(coords));
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            // visit implicit geometry to make sure only its reference point is changed
            if (implicitGeometry.isSetReferencePoint() && implicitGeometry.getReferencePoint().isSetPoint()) {
                Point point = implicitGeometry.getReferencePoint().getPoint();
                List<Double> coords = point.toList3d();
                point.unsetCoord();
                point.unsetCoordinates();
                point.setPos(adaptPosition(coords));
            }
        }

        @Override
        public void visit(AbstractFeature feature) {
            // update envelope
            if (feature.isSetBoundedBy() && feature.getBoundedBy().isSetEnvelope()) {
                BoundingBox bbox = feature.getBoundedBy().getEnvelope().toBoundingBox();
                if (bbox != null) {
                    bbox.getLowerCorner().setZ(bbox.getLowerCorner().getZ() + correction);
                    bbox.getUpperCorner().setZ(bbox.getUpperCorner().getZ() + correction);
                    feature.getBoundedBy().setEnvelope(bbox);
                }
            }
        }

        private DirectPositionList adaptPositionList(List<Double> coords) {
            for (int i = 0; i < coords.size(); i += 3)
                coords.set(i + 2, coords.get(i + 2) + correction);

            DirectPositionList posList = new DirectPositionList();
            posList.setSrsDimension(3);
            posList.setValue(coords);
            return posList;
        }

        private DirectPosition adaptPosition(List<Double> coords) {
            coords.set(2, coords.get(2) + correction);
            DirectPosition pos = new DirectPosition();
            pos.setValue(coords);
            pos.setSrsDimension(3);
            return pos;
        }
    }
}
