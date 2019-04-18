/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.common.helper;

import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.xml.io.reader.ParentInfo;
import org.citygml4j.xml.io.writer.CityModelInfo;

public class CityModelInfoHelper {

    public static CityModelInfo getCityModelInfo(CityGML cityGML, ParentInfo parentInfo) {
        if (parentInfo == null)
            return cityGML instanceof CityModel ? new CityModelInfo((CityModel) cityGML) : new CityModelInfo();
        else if (parentInfo.getCityGMLClass() == CityGMLClass.CITY_MODEL)
            return new CityModelInfo(parentInfo);
        else
            return getCityModelInfo(cityGML, parentInfo.getParentInfo());
    }

}
