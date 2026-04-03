/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometryProperty;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class GlobalObjectReader {
    private final EnumSet<GlobalObjectType> types;
    private boolean withTemplateAppearances;

    private GlobalObjectReader(EnumSet<GlobalObjectType> types) {
        this.types = types;
    }

    public static GlobalObjectReader defaults() {
        return new GlobalObjectReader(EnumSet.allOf(GlobalObjectType.class));
    }

    public static GlobalObjectReader of(GlobalObjectType... types) {
        return new GlobalObjectReader(EnumSet.copyOf(Arrays.asList(types)));
    }

    public static GlobalObjectReader of(EnumSet<GlobalObjectType> types) {
        return new GlobalObjectReader(types);
    }

    public static GlobalObjectReader onlyAppearances() {
        return new GlobalObjectReader(EnumSet.of(GlobalObjectType.APPEARANCE));
    }

    public static GlobalObjectReader onlyCityObjectGroups() {
        return new GlobalObjectReader(EnumSet.of(GlobalObjectType.CITY_OBJECT_GROUP));
    }

    public static GlobalObjectReader onlyImplicitGeometries() {
        return new GlobalObjectReader(EnumSet.of(GlobalObjectType.IMPLICIT_GEOMETRY));
    }

    public GlobalObjectReader withTemplateAppearances(boolean withTemplateAppearances) {
        this.withTemplateAppearances = withTemplateAppearances;
        return this;
    }

    public GlobalObjectHelper read(InputFile file, CityGMLContext context) throws ExecutionException {
        try {
            GlobalObjectHelper globalObjectHelper = new GlobalObjectHelper();
            try (CityGMLReader reader = createReader(file, context)) {
                while (reader.hasNext()) {
                    AbstractFeature feature = reader.next();
                    if (feature instanceof Appearance) {
                        if (types.contains(GlobalObjectType.APPEARANCE)) {
                            globalObjectHelper.add((Appearance) feature, reader.getName());
                        }
                    } else if (feature instanceof CityObjectGroup) {
                        if (types.contains(GlobalObjectType.CITY_OBJECT_GROUP)) {
                            globalObjectHelper.add((CityObjectGroup) feature, reader.getName());
                        }
                    } else if (types.contains(GlobalObjectType.IMPLICIT_GEOMETRY)) {
                        GeometryInfo geometryInfo = feature.getGeometryInfo();
                        for (int lod : geometryInfo.getLods()) {
                            geometryInfo.getImplicitGeometries(lod).stream()
                                    .filter(ImplicitGeometryProperty::isSetInlineObject)
                                    .map(ImplicitGeometryProperty::getObject)
                                    .forEach(implicitGeometry ->
                                            globalObjectHelper.add(implicitGeometry, lod, withTemplateAppearances));
                        }

                        geometryInfo.getNonLodImplicitGeometries().stream()
                                .filter(ImplicitGeometryProperty::isSetInlineObject)
                                .map(ImplicitGeometryProperty::getObject)
                                .forEach(implicitGeometry ->
                                        globalObjectHelper.add(implicitGeometry, withTemplateAppearances));
                    }
                }
            }

            return globalObjectHelper;
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }

    private CityGMLReader createReader(InputFile file, CityGMLContext context) throws ExecutionException {
        try {
            CityGMLInputFactory in = context.createCityGMLInputFactory()
                    .withChunking(ChunkOptions.defaults())
                    .withIdCreator(new IdCreator());

            CityGMLReader reader = in.createCityGMLReader(file.getFile());
            if (!types.contains(GlobalObjectType.IMPLICIT_GEOMETRY)) {
                Set<String> localNames = new HashSet<>();
                if (types.contains(GlobalObjectType.APPEARANCE)) {
                    localNames.add("Appearance");
                }

                if (types.contains(GlobalObjectType.CITY_OBJECT_GROUP)) {
                    localNames.add("CityObjectGroup");
                }

                return in.createFilteredCityGMLReader(reader, name -> localNames.contains(name.getLocalPart())
                        && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
            } else if (types.size() == 1 && types.contains(GlobalObjectType.IMPLICIT_GEOMETRY)) {
                return in.createFilteredCityGMLReader(reader, name -> !"Appearance".equals(name.getLocalPart())
                        || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
            }

            return reader;
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }
}
