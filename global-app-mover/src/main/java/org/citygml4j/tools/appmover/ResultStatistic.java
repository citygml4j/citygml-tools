package org.citygml4j.tools.appmover;

import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.X3DMaterial;
import org.citygml4j.model.citygml.core.AbstractCityObject;

import java.util.concurrent.ConcurrentHashMap;

public class ResultStatistic {
    private final ConcurrentHashMap<String, Integer> counter = new ConcurrentHashMap<>();

    void increment(Class<? extends CityGML> cityGML) {
        counter.merge(cityGML.getName(), 1, Integer::sum);
    }

    int get(Class<? extends CityGML> cityGML) {
        return counter.getOrDefault(cityGML.getName(), 0);
    }

    public int getCityObjects() {
        return get(AbstractCityObject.class);
    }

    public int getAppearances() {
        return get(Appearance.class);
    }

    public int getParameterizedTextures() {
        return get(ParameterizedTexture.class);
    }

    public int getGeoreferencedTextures() {
        return get(GeoreferencedTexture.class);
    }

    public int getX3DMaterials() {
        return get(X3DMaterial.class);
    }

}