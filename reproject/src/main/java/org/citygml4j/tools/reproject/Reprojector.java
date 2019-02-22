package org.citygml4j.tools.reproject;

import org.citygml4j.geometry.BoundingBox;
import org.citygml4j.geometry.Matrix;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.citygml.relief.AbstractReliefComponent;
import org.citygml4j.model.gml.GML;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.model.gml.feature.BoundingShape;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.primitives.AbstractCurveSegment;
import org.citygml4j.model.gml.geometry.primitives.Curve;
import org.citygml4j.model.gml.geometry.primitives.DirectPosition;
import org.citygml4j.model.gml.geometry.primitives.DirectPositionList;
import org.citygml4j.model.gml.geometry.primitives.Envelope;
import org.citygml4j.model.gml.geometry.primitives.LineString;
import org.citygml4j.model.gml.geometry.primitives.LineStringSegment;
import org.citygml4j.model.gml.geometry.primitives.LinearRing;
import org.citygml4j.model.gml.geometry.primitives.Point;
import org.citygml4j.model.gml.geometry.primitives.PointProperty;
import org.citygml4j.model.gml.geometry.primitives.Polygon;
import org.citygml4j.tools.reproject.util.CRSUtil;
import org.citygml4j.tools.reproject.util.SRSNameHelper;
import org.citygml4j.util.walker.GMLWalker;
import org.citygml4j.util.walker.GeometryWalker;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Reprojector {
    private final TransformationWalker transformationWalker = new TransformationWalker();
    private final CleanupWalker cleanupWalker = new CleanupWalker();

    private final CRSUtil crsUtil = new CRSUtil();
    private final SRSNameHelper srsNameHelper = new SRSNameHelper();

    private String targetCRS;
    private String targetSRSName;
    private boolean keepHeightValues;

    private boolean sourceSwapXY;

    Reprojector() {

    }

    void setTargetCRS(String crs, boolean forceXY) throws ReprojectionException {
        targetCRS = setCRS(crs, forceXY, true);
        if (targetSRSName == null)
            targetSRSName = targetCRS;
    }

    void setSourceCRS(String crs) throws ReprojectionException {
        String sourceCRS = setCRS(crs, false, false);
        srsNameHelper.forceSRSName(sourceCRS);
    }

    void setTargetSRSName(String srsName) {
        targetSRSName = srsName;
    }

    void setKeepHeightValues(boolean keepHeightValues) {
        this.keepHeightValues = keepHeightValues;
    }

    void setSourceSwapXY(boolean sourceSwapXY) {
        this.sourceSwapXY = sourceSwapXY;
    }

    public void setFallbackSRSName(String srsName) {
        srsNameHelper.setFallbackSRSName(srsName);
    }

    public String getTargetCRSAsWKT() {
        if (targetCRS != null) {
            try {
                return crsUtil.getCoordinateReferenceSystem(targetCRS).toWKT();
            } catch (ReprojectionException e) {
                //
            }
        }

        return null;
    }

    public void reproject(AbstractFeature feature) throws ReprojectionException {
        try {
            feature.accept(transformationWalker);
            feature.accept(cleanupWalker);
        } catch (RuntimeException e) {
            throw new ReprojectionException("Failed to reproject feature with gml:id '" + feature.getId() + "'.", e);
        }
    }

    public void reproject(BoundingShape boundingShape) throws ReprojectionException {
        if (boundingShape.isSetEnvelope()) {
            Envelope envelope = boundingShape.getEnvelope();
            String srsName = srsNameHelper.getSRSName(envelope);

            BoundingBox bbox = envelope.toBoundingBox();
            if (bbox != null) {
                List<Double> coords = transform(bbox.getLowerCorner().toList(), srsName, envelope);
                bbox.setLowerCorner(coords.get(0), coords.get(1), coords.get(2));

                coords = transform(bbox.getUpperCorner().toList(), srsName, envelope);
                bbox.setUpperCorner(coords.get(0), coords.get(1), coords.get(2));

                boundingShape.setEnvelope(bbox);
                boundingShape.getEnvelope().setSrsName(targetSRSName);
            }
        }
    }

    private final class TransformationWalker extends GMLWalker {

        @Override
        public void visit(LinearRing linearRing) {
            String srsName = getSRSName(linearRing);

            List<Double> coords = linearRing.toList3d();
            linearRing.unsetPosOrPointPropertyOrPointRep();
            linearRing.unsetCoord();
            linearRing.unsetCoordinates();
            linearRing.setPosList(transformPositionList(coords, srsName, linearRing));
        }

        @Override
        public void visit(LineString lineString) {
            String srsName = getSRSName(lineString);

            List<Double> coords = lineString.toList3d();
            lineString.unsetPosOrPointPropertyOrPointRepOrCoord();
            lineString.unsetCoordinates();
            lineString.setPosList(transformPositionList(coords, srsName, lineString));
        }

        @Override
        public void visit(Curve curve) {
            String srsName = getSRSName(curve);

            if (curve.isSetSegments() && curve.getSegments().isSetCurveSegment()) {
                for (AbstractCurveSegment segment : curve.getSegments().getCurveSegment()) {
                    if (segment instanceof LineStringSegment) {
                        LineStringSegment lineString = (LineStringSegment) segment;

                        List<Double> coords = lineString.toList3d();
                        lineString.unsetPosOrPointPropertyOrPointRep();
                        lineString.unsetCoordinates();
                        lineString.setPosList(transformPositionList(coords, srsName, lineString));
                    }
                }
            }
        }

        @Override
        public void visit(Point point) {
            String srsName = getSRSName(point);

            List<Double> coords = point.toList3d();
            point.unsetCoord();
            point.unsetCoordinates();
            point.setPos(transformPosition(coords, srsName, point));
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            // move translation of transformation matrix to reference point
            if (implicitGeometry.isSetTransformationMatrix()) {
                Matrix matrix = implicitGeometry.getTransformationMatrix().getMatrix();
                List<Double> coords = implicitGeometry.isSetReferencePoint() && implicitGeometry.getReferencePoint().isSetPoint() ?
                        implicitGeometry.getReferencePoint().getPoint().toList3d() : Arrays.asList(new Double[3]);

                coords.set(0, coords.get(0) + matrix.get(0, 3));
                coords.set(1, coords.get(1) + matrix.get(1, 3));
                coords.set(2, coords.get(2) + matrix.get(2, 3));

                matrix.set(0, 3, 0);
                matrix.set(1, 3, 0);
                matrix.set(2, 3, 0);

                Point point = new Point();
                DirectPosition pos = new DirectPosition();
                pos.setValue(coords);
                point.setPos(pos);

                implicitGeometry.setReferencePoint(new PointProperty(point));
            }

            // transform reference point but not the template geometry
            if (implicitGeometry.isSetReferencePoint() && implicitGeometry.getReferencePoint().isSetPoint())
                visit(implicitGeometry.getReferencePoint().getPoint());
        }

        @Override
        public void visit(GeoreferencedTexture georeferencedTexture) {
            if (georeferencedTexture.isSetReferencePoint() && georeferencedTexture.getReferencePoint().isSetPoint()) {
                Point point = georeferencedTexture.getReferencePoint().getPoint();
                visit(point);

                // make sure the reference point is 2D
                DirectPosition pos = point.getPos();
                pos.setSrsDimension(2);
                pos.getValue().remove(2);
            }
        }

        @Override
        public void visit(AbstractReliefComponent reliefComponent) {
            if (reliefComponent.isSetExtent() && reliefComponent.getExtent().isSetPolygon()) {
                Polygon polygon = reliefComponent.getExtent().getPolygon();
                visit(polygon);

                // make sure the extent polygon is 2D
                polygon.accept(new GeometryWalker() {
                    public void visit(LinearRing linearRing) {
                        DirectPositionList posList = linearRing.getPosList();
                        posList.setSrsDimension(2);
                        posList.setValue(IntStream.range(0, posList.getValue().size())
                                .filter(i -> (i + 1) % 3 != 0)
                                .mapToObj(i -> posList.getValue().get(i))
                                .collect(Collectors.toList()));
                    }
                });
            }

            super.visit(reliefComponent);
        }

        private DirectPositionList transformPositionList(List<Double> coords, String srsName, GML gml) {
            DirectPositionList posList = new DirectPositionList();
            posList.setSrsDimension(3);
            posList.setValue(transform(coords, srsName, gml));
            return posList;
        }

        private DirectPosition transformPosition(List<Double> coords, String srsName, GML gml) {
            DirectPosition pos = new DirectPosition();
            pos.setValue(transform(coords, srsName, gml));
            pos.setSrsDimension(3);
            return pos;
        }

        private String getSRSName(AbstractGML gml) {
            try {
                return srsNameHelper.getSRSName(gml);
            } catch (ReprojectionException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private final class CleanupWalker extends GMLWalker {
        @Override
        public void visit(AbstractFeature feature) {
            if (feature.isSetBoundedBy()) {
                try {
                    reproject(feature.getBoundedBy());
                } catch (ReprojectionException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }

            super.visit(feature);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            geometry.unsetSrsName();
            super.visit(geometry);
        }
    }

    private List<Double> transform(List<Double> coords, String srsName, GML gml) {
        if (coords == null)
            throw new RuntimeException("Failed to retrieve coordinates from " + gml.getGMLClass() + ".");

        if (srsName == null)
            throw new RuntimeException("Missing CRS definition on " + gml.getGMLClass() + ".");

        try {
            MathTransform transform = crsUtil.getTransformation(srsName, targetCRS);
            double[] srsPts = new double[transform.getSourceDimensions()];
            double[] dstPts = new double[transform.getTargetDimensions()];

            for (int i = 0; i < coords.size(); i += 3) {
                if (!sourceSwapXY) {
                    srsPts[0] = coords.get(i);
                    srsPts[1] = coords.get(i + 1);
                } else {
                    srsPts[0] = coords.get(i + 1);
                    srsPts[1] = coords.get(i);
                }

                if (transform.getSourceDimensions() == 3)
                    srsPts[2] = coords.get(i + 2);

                transform.transform(srsPts, 0, dstPts, 0, 1);

                coords.set(i, dstPts[0]);
                coords.set(i + 1, dstPts[1]);
                if (transform.getSourceDimensions() == 3 && !keepHeightValues)
                    coords.set(i + 2, dstPts[2]);
            }

        } catch (ReprojectionException | TransformException e) {
            throw new RuntimeException("Failed to transform coordinates.", e);
        }

        return coords;
    }

    private String setCRS(String crs, boolean forceXY, boolean isTargetCRS) throws ReprojectionException {
        if (crs.matches("[0-9]+")) {
            int epsg = Integer.parseInt(crs);
            crsUtil.getCoordinateReferenceSystem(epsg, forceXY);
            return crsUtil.getSrsName(epsg);
        }

        else if (crs.matches("^.*?((CRS)|(CS)|(OPERATION))\\[.+")) {
            CoordinateReferenceSystem tmp = crsUtil.getCoordinateReferenceSystemFromWKT(crs);
            if (isTargetCRS && targetSRSName == null) {
                targetSRSName = crsUtil.lookupIdentifier(tmp, true);
                if (targetSRSName == null)
                    throw new ReprojectionException("Failed to find identifier for the WKT CRS '" + tmp.getName() + "'.");
            }

            return crs;
        }

        else {
            crsUtil.getCoordinateReferenceSystem(crs, forceXY);
            return crs;
        }
    }
}
