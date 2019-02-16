package org.citygml4j.tools.heightchanger;

public enum HeightMode {
    RELATIVE,
    ABSOLUTE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
