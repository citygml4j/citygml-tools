package org.citygml4j.tools.lodfilter;

public enum LodFilterMode {
    KEEP,
    REMOVE,
    MINIMUM,
    MAXIMUM;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
