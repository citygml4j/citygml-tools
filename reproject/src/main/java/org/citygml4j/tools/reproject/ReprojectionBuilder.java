package org.citygml4j.tools.reproject;

public class ReprojectionBuilder {
    private String targetCRS;
    private int targetEPSG;
    private String targetSRSName;
    private boolean targetForceXY;
    private boolean keepHeightValues;
    private String sourceCRS;
    private int sourceEPSG;
    private boolean sourceSwapXY;

    public static ReprojectionBuilder defaults() {
        return new ReprojectionBuilder();
    }

    public ReprojectionBuilder withTargetCRS(String crs) {
        targetCRS = crs;
        return this;
    }

    public ReprojectionBuilder withTargetCRS(int epsg) {
        targetEPSG = epsg;
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
        sourceEPSG = epsg;
        return this;
    }

    public ReprojectionBuilder swapXYAxisOrderForSourceGeometries(boolean sourceSwapXY) {
        this.sourceSwapXY = sourceSwapXY;
        return this;
    }

    public Reprojector build() throws ReprojectionBuilderException {
        if (targetCRS == null && targetEPSG <= 0)
            throw new ReprojectionBuilderException("No target CRS defined." );

        Reprojector reprojector = new Reprojector();

        try {
            if (targetEPSG > 0)
                reprojector.setTargetCRS(targetEPSG, targetForceXY);
            else {
                int epsg = 0;
                try {
                    epsg = Integer.parseInt(targetCRS);
                } catch (NumberFormatException e) {
                    //
                }

                if (epsg > 0)
                    reprojector.setTargetCRS(epsg, targetForceXY);
                else {
                    if (targetCRS.matches("^.*?((CRS)|(CS)|(OPERATION))\\[.+"))
                        reprojector.setTargetCRSFromWKT(targetCRS);
                    else
                        reprojector.setTargetCRS(targetCRS, targetForceXY);
                }
            }

            if (targetSRSName != null)
                reprojector.setTargetSRSName(targetSRSName);

        } catch (ReprojectionException e) {
            throw new ReprojectionBuilderException("Failed to set the target CRS.", e);
        }

        try {
            if (sourceEPSG > 0)
                reprojector.setSourceCRS(sourceEPSG);
            else if (sourceCRS != null) {
                int epsg = 0;
                try {
                    epsg = Integer.parseInt(sourceCRS);
                } catch (NumberFormatException e) {
                    //
                }

                if (epsg > 0)
                    reprojector.setSourceCRS(epsg);
                else {
                    if (sourceCRS.matches("^.*?((CRS)|(CS)|(OPERATION))\\[.+"))
                        reprojector.setSourceCRSFromWKT(sourceCRS);
                    else
                        reprojector.setSourceCRS(sourceCRS);
                }
            }
        } catch (ReprojectionException e) {
            throw new ReprojectionBuilderException("Failed to set the source CRS.", e);
        }

        reprojector.setKeepHeightValues(keepHeightValues);
        reprojector.setSourceSwapXY(sourceSwapXY);

        return reprojector;
    }

}
