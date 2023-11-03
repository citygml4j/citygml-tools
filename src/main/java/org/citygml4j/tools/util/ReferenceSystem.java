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

import org.citygml4j.tools.ExecutionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReferenceSystem {
    public static final Pattern CRS_URL = Pattern.compile("^https?://www.opengis.net/def/crs/(.+?)/(.*?)/(\\d+)$");
    public static final Pattern CRS_URN = Pattern.compile("^urn:ogc:def:crs(?:,crs)?:(.+?):(.*?):(\\d+).*");
    public static final Pattern CRS_SHORT = Pattern.compile("^(?:([^:]+):)?(\\d+)$");
    public static final Pattern CRS_WKT = Pattern.compile("^.+?((CRS)|(CS))\\s*\\[.+", Pattern.DOTALL);

    private CoordinateReferenceSystem crs;
    private String authority = "EPSG";
    private String version = "0";
    private int code;

    private ReferenceSystem() {
    }

    public static ReferenceSystem of(String name) throws ExecutionException {
        return of(name, false);
    }

    public static ReferenceSystem of(String name, boolean forceLongitudeFirst) throws ExecutionException {
        try {
            ReferenceSystem referenceSystem = new ReferenceSystem();
            Matcher matcher = CRS_WKT.matcher(name);
            if (matcher.matches()) {
                referenceSystem.setCRS(CRS.parseWKT(name));
            } else {
                if (matcher.usePattern(CRS_SHORT).matches()) {
                    referenceSystem.setAuthority(matcher.group(1));
                    referenceSystem.setCode(Integer.parseInt(matcher.group(2)));
                } else if (matcher.usePattern(CRS_URL).matches() || matcher.usePattern(CRS_URN).matches()) {
                    referenceSystem.setAuthority(matcher.group(1));
                    referenceSystem.setVersion(matcher.group(2));
                    referenceSystem.setCode(Integer.parseInt(matcher.group(3)));
                }

                try {
                    referenceSystem.setCRS(CRS.decode(name, forceLongitudeFirst));
                } catch (FactoryException e) {
                    //
                }

                if (referenceSystem.getCRS() == null) {
                    if (referenceSystem.getCode() > 0) {
                        referenceSystem.setCRS(CRS.decode(referenceSystem.toShortNotation(), forceLongitudeFirst));
                    } else {
                        throw new ExecutionException("Failed to interpret CRS string '" + name + "'.");
                    }
                }
            }

            return referenceSystem;
        } catch (FactoryException e) {
            throw new ExecutionException("Failed to create CRS from '" + name + "'.", e);
        }
    }

    public CoordinateReferenceSystem getCRS() {
        return crs;
    }

    void setCRS(CoordinateReferenceSystem crs) {
        this.crs = crs;
    }

    public String getAuthority() {
        return authority;
    }

    void setAuthority(String authority) {
        this.authority = authority != null && !authority.isEmpty() ? authority : "EPSG";
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version != null && !version.isEmpty() ? version : "0";
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = Math.max(code, 0);
    }

    public String toShortNotation() {
        return authority + ":" + code;
    }


    public String toURL() {
        return "http://www.opengis.net/def/crs/" + authority + "/" + version + "/" + code;
    }

    public String toURN() {
        return "urn:ogc:def:crs:" + authority + ":" + (!version.equals("0") ? version : "") + ":" + code;
    }
}
