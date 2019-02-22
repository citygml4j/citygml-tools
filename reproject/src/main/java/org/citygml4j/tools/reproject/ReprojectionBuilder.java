package org.citygml4j.tools.reproject;

public class ReprojectionBuilder {
    private String targetCRS;
    private String targetSRSName;
    private boolean targetForceXY;
    private boolean keepHeightValues;
    private String sourceCRS;
    private boolean sourceSwapXY;

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

        return reprojector;
    }

}
