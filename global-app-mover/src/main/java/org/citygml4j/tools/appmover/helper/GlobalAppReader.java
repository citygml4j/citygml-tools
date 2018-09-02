package org.citygml4j.tools.appmover.helper;

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.module.Modules;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.reader.FeatureReadMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GlobalAppReader {
    private final CityGMLBuilder cityGMLBuilder;

    public GlobalAppReader(CityGMLBuilder cityGMLBuilder) {
        this.cityGMLBuilder = cityGMLBuilder;
    }

    public List<Appearance> readGlobalApps(Path file) throws CityGMLBuilderException, CityGMLReadException {
        CityGMLInputFactory in = cityGMLBuilder.createCityGMLInputFactory();
        in.setProperty(CityGMLInputFactory.FEATURE_READ_MODE, FeatureReadMode.SPLIT_PER_COLLECTION_MEMBER);

        List<Appearance> appearances = new ArrayList<>();
        try (CityGMLReader reader = in.createFilteredCityGMLReader(in.createCityGMLReader(file.toFile()),
                name -> name.getLocalPart().equals("Appearance")
                        && Modules.isCityGMLModuleNamespace(name.getNamespaceURI()))) {
            while (reader.hasNext())
                appearances.add((Appearance) reader.nextFeature());
        }

        return appearances;
    }

}
