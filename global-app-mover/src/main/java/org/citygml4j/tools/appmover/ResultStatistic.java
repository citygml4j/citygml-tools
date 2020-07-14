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