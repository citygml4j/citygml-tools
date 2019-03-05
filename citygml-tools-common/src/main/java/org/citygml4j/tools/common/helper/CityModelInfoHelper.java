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
