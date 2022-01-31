/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2020 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.reproject.util;

import org.citygml4j.model.gml.GML;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.gml.geometry.SRSReferenceGroup;
import org.citygml4j.model.gml.geometry.primitives.*;
import org.citygml4j.tools.reproject.ReprojectionException;
import org.citygml4j.util.walker.GMLFunctionWalker;

public class SRSNameHelper {
    private final SRSNameWalker walker = new SRSNameWalker();
    private String forceSRSName;
    private String fallbackSRSName;

    public void forceSRSName(String forceSRSName) {
        this.forceSRSName = forceSRSName;
    }

    public void setFallbackSRSName(String fallbackSRSName) {
        this.fallbackSRSName = fallbackSRSName;
    }

    public String getSRSName(AbstractGML gml) throws ReprojectionException {
        if (forceSRSName != null)
            return forceSRSName;

        try {
            return gml.accept(walker);
        } catch (RuntimeException e) {
            throw new ReprojectionException(e.getMessage());
        }
    }

    public String getSRSName(Envelope envelope) throws ReprojectionException {
        if (forceSRSName != null)
            return forceSRSName;

        try {
            String srsName = null;

            if (envelope.isSetLowerCorner())
                srsName = lookupAndCheckSRSName(srsName, envelope.getLowerCorner(), envelope);

            if (envelope.isSetUpperCorner())
                srsName = lookupAndCheckSRSName(srsName, envelope.getUpperCorner(), envelope);

            if (envelope.isSetPos()) {
                for (DirectPosition pos : envelope.getPos())
                    srsName = lookupAndCheckSRSName(srsName, pos, envelope);
            }

            return srsName;
        } catch (RuntimeException e) {
            throw new ReprojectionException(e.getMessage());
        }
    }

    private final class SRSNameWalker extends GMLFunctionWalker<String> {

        @Override
        public String apply(LinearRing linearRing) {
            String srsName = null;

            if (linearRing.isSetPosList())
                srsName = lookupAndCheckSRSName(srsName, linearRing.getPosList(), linearRing);

            if (linearRing.isSetPosOrPointPropertyOrPointRep()) {
                for (PosOrPointPropertyOrPointRep property : linearRing.getPosOrPointPropertyOrPointRep())
                    srsName = lookupAndCheckSRSName(srsName, property, linearRing);
            }

            if (srsName == null)
                srsName = lookupSRSName(linearRing);

            return srsName;
        }

        @Override
        public String apply(LineString lineString) {
            String srsName = null;

            if (lineString.isSetPosList())
                srsName = lookupAndCheckSRSName(srsName, lineString.getPosList(), lineString);

            if (lineString.isSetPosOrPointPropertyOrPointRepOrCoord()) {
                for (PosOrPointPropertyOrPointRepOrCoord property : lineString.getPosOrPointPropertyOrPointRepOrCoord()) {
                    if (property.isSetPos())
                        srsName = lookupAndCheckSRSName(srsName, property.getPos(), lineString);
                    else {
                        Point point = null;
                        if (property.isSetPointProperty() && property.getPointProperty().isSetPoint())
                            point = property.getPointProperty().getPoint();
                        else if (property.isSetPointRep() && property.getPointRep().isSetPoint())
                            point = property.getPointRep().getPoint();

                        if (point != null && point.isSetPos())
                            srsName = lookupAndCheckSRSName(srsName, point.getPos(), lineString);
                    }
                }
            }

            if (srsName == null)
                srsName = lookupSRSName(lineString);

            return srsName;
        }

        @Override
        public String apply(Curve curve) {
            String srsName = lookupSRSName(curve);

            if (curve.isSetSegments() && curve.getSegments().isSetCurveSegment()) {
                for (AbstractCurveSegment segment : curve.getSegments().getCurveSegment()) {
                    if (segment instanceof LineStringSegment) {
                        LineStringSegment lineString = (LineStringSegment) segment;

                        if (lineString.isSetPosList())
                            srsName = lookupAndCheckSRSName(srsName, lineString.getPosList(), lineString);

                        if (lineString.isSetPosOrPointPropertyOrPointRep()) {
                            for (PosOrPointPropertyOrPointRep property : lineString.getPosOrPointPropertyOrPointRep())
                                srsName = lookupAndCheckSRSName(srsName, property, lineString);
                        }
                    }
                }
            }

            return srsName;
        }

        @Override
        public String apply(Point point) {
            String srsName = null;

            if (point.isSetPos())
                srsName = lookupAndCheckSRSName(srsName, point.getPos(), point);

            if (srsName == null)
                srsName = lookupSRSName(point);

            return srsName;
        }
    }

    private String lookupSRSName(SRSReferenceGroup reference) {
        String srsName = reference.getSrsName();
        if (srsName == null)
            srsName = reference.getInheritedSrsName();
        if (srsName == null)
            srsName = fallbackSRSName;

        return srsName;
    }

    private String lookupAndCheckSRSName(String srsName, SRSReferenceGroup reference, GML gml) {
        String candidate = lookupSRSName(reference);
        if (srsName != null && candidate != null && !srsName.equals(candidate))
            throw new RuntimeException("Multiple CRS definitions on " + gml.getGMLClass() + " are not supported.");

        return candidate != null ? candidate : srsName;
    }

    private String lookupAndCheckSRSName(String srsName, PosOrPointPropertyOrPointRep property, GML gml) {
        if (property.isSetPos())
            srsName = lookupAndCheckSRSName(srsName, property.getPos(), gml);
        else {
            Point point = null;
            if (property.isSetPointProperty() && property.getPointProperty().isSetPoint())
                point = property.getPointProperty().getPoint();
            else if (property.isSetPointRep() && property.getPointRep().isSetPoint())
                point = property.getPointRep().getPoint();

            if (point != null && point.isSetPos())
                srsName = lookupAndCheckSRSName(srsName, point.getPos(), gml);
        }

        return srsName;
    }

}
