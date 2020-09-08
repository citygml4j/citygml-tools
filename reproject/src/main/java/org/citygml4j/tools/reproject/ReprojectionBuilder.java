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

package org.citygml4j.tools.reproject;

public class ReprojectionBuilder {
    private String targetCRS;
    private String targetSRSName;
    private boolean targetForceXY;
    private boolean keepHeightValues;
    private String sourceCRS;
    private boolean sourceSwapXY;
    private boolean lenientTransform;

    public static ReprojectionBuilder defaults() {
        return new ReprojectionBuilder();
    }

    public ReprojectionBuilder withTargetCRS(String crs) {
        targetCRS = crs;
        return this;
    }

    public ReprojectionBuilder withTargetCRS(int epsg) {
        targetCRS = String.valueOf(epsg);
        return this;
    }

    public ReprojectionBuilder withTargetSRSName(String srsName) {
        targetSRSName = srsName;
        return this;
    }

    public ReprojectionBuilder forceXYAxisOrderForTargetCRS(boolean forceLongitudeFirst) {
        this.targetForceXY = forceLongitudeFirst;
        return this;
    }

    public ReprojectionBuilder keepHeightValues(boolean keepHeightValues) {
        this.keepHeightValues = keepHeightValues;
        return this;
    }

    public ReprojectionBuilder withSourceCRS(String crs) {
        sourceCRS = crs;
        return this;
    }

    public ReprojectionBuilder withSourceCRS(int epsg) {
        sourceCRS = String.valueOf(epsg);
        return this;
    }

    public ReprojectionBuilder swapXYAxisOrderForSourceGeometries(boolean sourceSwapXY) {
        this.sourceSwapXY = sourceSwapXY;
        return this;
    }

    public ReprojectionBuilder lenientTransform(boolean lenientTransform) {
        this.lenientTransform = lenientTransform;
        return this;
    }

    public Reprojector build() throws ReprojectionBuilderException {
        if (targetCRS == null)
            throw new ReprojectionBuilderException("No target CRS defined." );

        Reprojector reprojector = new Reprojector();

        try {
            reprojector.setTargetCRS(targetCRS, targetForceXY);
            if (targetSRSName != null)
                reprojector.setTargetSRSName(targetSRSName);
        } catch (ReprojectionException e) {
            throw new ReprojectionBuilderException("Failed to set the target CRS.", e);
        }

        try {
            if (sourceCRS != null)
                reprojector.setSourceCRS(sourceCRS);
        } catch (ReprojectionException e) {
            throw new ReprojectionBuilderException("Failed to set the source CRS.", e);
        }

        reprojector.setKeepHeightValues(keepHeightValues);
        reprojector.setSourceSwapXY(sourceSwapXY);
        reprojector.setLenientTransform(lenientTransform);

        return reprojector;
    }

}
