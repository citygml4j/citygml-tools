package org.citygml4j.tools.common.helper;

import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.xml.io.reader.ParentInfo;
import org.citygml4j.xml.io.writer.CityModelInfo;

public class CityModelInfoReader {

    public static CityModelInfo getCityModelInfo(CityGML cityGML, ParentInfo parentInfo) {
        if (parentInfo == null)
            return cityGML instanceof CityModel ? new CityModelInfo((CityModel) cityGML) : new CityModelInfo();

        return getCityModelInfo(cityGML, parentInfo.getParentInfo());
    }

}
