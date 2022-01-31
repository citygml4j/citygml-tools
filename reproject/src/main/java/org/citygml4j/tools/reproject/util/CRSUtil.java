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

package org.citygml4j.tools.reproject.util;

import org.citygml4j.tools.common.srs.SrsNameParser;
import org.citygml4j.tools.common.srs.SrsParseException;
import org.citygml4j.tools.reproject.ReprojectionException;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.util.concurrent.ConcurrentHashMap;

public class CRSUtil {
    private final ConcurrentHashMap<String, CoordinateReferenceSystem> referenceSystems;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, MathTransform>> transformations;
    private final SrsNameParser parser;

    public CRSUtil() {
        referenceSystems = new ConcurrentHashMap<>();
        transformations = new ConcurrentHashMap<>();
        parser = new SrsNameParser();
    }

    public CoordinateReferenceSystem getCoordinateReferenceSystem(int epsg) throws ReprojectionException {
        return getCoordinateReferenceSystem(epsg, false);
    }

    public CoordinateReferenceSystem getCoordinateReferenceSystem(int epsg, boolean forceXY) throws ReprojectionException {
        return getCoordinateReferenceSystem(getSrsName(epsg), forceXY, false);
    }

    public CoordinateReferenceSystem getCoordinateReferenceSystem(String srsName) throws ReprojectionException {
        return getCoordinateReferenceSystem(srsName, false);
    }

    public CoordinateReferenceSystem getCoordinateReferenceSystem(String srsName, boolean forceXY) throws ReprojectionException {
        return getCoordinateReferenceSystem(srsName, forceXY, true);
    }

    private CoordinateReferenceSystem getCoordinateReferenceSystem(String srsName, boolean forceXY, boolean useFallback) throws ReprojectionException {
        CoordinateReferenceSystem crs = referenceSystems.get(srsName);

        if (crs == null) {
            try {
                crs = CRS.decode(srsName, forceXY);
            } catch (FactoryException e) {
                //
            }

            if (crs == null && useFallback) {
                try {
                    int epsg = parser.getEPSGCode(srsName);
                    crs = CRS.decode(getSrsName(epsg), forceXY);
                } catch (SrsParseException | FactoryException e) {
                    //
                }
            }

            if (crs == null)
                throw new ReprojectionException("Failed to find CRS definition for '" + srsName + "'.");

            CoordinateReferenceSystem previous = referenceSystems.putIfAbsent(srsName, crs);
            if (previous != null)
                crs = previous;
        }

        return crs;
    }

    public CoordinateReferenceSystem getCoordinateReferenceSystemFromWKT(String wkt) throws ReprojectionException {
        CoordinateReferenceSystem crs = referenceSystems.get(wkt);

        if (crs == null) {
            try {
                crs = CRS.parseWKT(wkt);
            } catch (FactoryException e) {
                throw new ReprojectionException("Failed to find CRS definition for WKT representation.", e);
            }

            CoordinateReferenceSystem previous = referenceSystems.putIfAbsent(wkt, crs);
            if (previous != null)
                crs = previous;
        }

        return crs;
    }

    public MathTransform getTransformation(String sourceSRSName, String targetSRSName, boolean lenient) throws ReprojectionException {
        ConcurrentHashMap<String, MathTransform> mathTransforms = transformations.computeIfAbsent(sourceSRSName, v -> new ConcurrentHashMap<>());
        MathTransform mathTransform = mathTransforms.get(targetSRSName);

        if (mathTransform == null) {
            CoordinateReferenceSystem sourceCRS = getCoordinateReferenceSystem(sourceSRSName);
            CoordinateReferenceSystem targetCRS = getCoordinateReferenceSystem(targetSRSName);

            try {
                mathTransform = CRS.findMathTransform(sourceCRS, targetCRS, lenient);
            } catch (FactoryException e) {
                throw new ReprojectionException("Failed to find a transformation.", e);
            }

            MathTransform previous = mathTransforms.putIfAbsent(targetSRSName, mathTransform);
            if (previous != null)
                mathTransform = previous;
        }

        return mathTransform;
    }

    public String lookupIdentifier(CoordinateReferenceSystem crs, boolean fullScan) {
        String identifier = null;

        try {
            identifier = CRS.lookupIdentifier(crs, fullScan);
            if (identifier == null)
                identifier = getSrsName(CRS.lookupEpsgCode(crs, fullScan));
        } catch (FactoryException e) {
            //
        }

        return identifier;
    }

    public String getSrsName(int epsg) {
        return "EPSG:" + epsg;
    }
}
