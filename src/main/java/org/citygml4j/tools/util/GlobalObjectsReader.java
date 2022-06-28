/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.tools.cli.ExecutionException;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.xmlobjects.stream.EventType;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.stream.XMLReaderFactory;

import javax.xml.namespace.QName;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

public class GlobalObjectsReader {
    private final EnumSet<GlobalObjects.Type> types;
    private final ChunkOptions chunkOptions;

    private GlobalObjectsReader(EnumSet<GlobalObjects.Type> types) {
        this.types = types;
        chunkOptions = ChunkOptions.empty().addCityModelMemberProperties();
    }

    public static GlobalObjectsReader defaults() {
        return new GlobalObjectsReader(EnumSet.allOf(GlobalObjects.Type.class));
    }

    public static GlobalObjectsReader of(GlobalObjects.Type... types) {
        return new GlobalObjectsReader(EnumSet.copyOf(Arrays.asList(types)));
    }

    public static GlobalObjectsReader onlyAppearances() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.APPEARANCE));
    }

    public static GlobalObjectsReader onlyCityObjectGroups() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.CITY_OBJECT_GROUP));
    }

    public static GlobalObjectsReader onlyImplicitGeometries() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.IMPLICIT_GEOMETRY));
    }

    public GlobalObjects read(Path file, CityGMLContext context) throws ExecutionException {
        GlobalObjects globalObjects = new GlobalObjects();
        boolean checkForTopLevel = !Collections.disjoint(types, GlobalObjects.Type.TOP_LEVEL_TYPES);
        boolean isTopLevel = false;

        try (XMLReader reader = XMLReaderFactory.newInstance(context.getXMLObjects()).createReader(file)) {
            while (reader.hasNext()) {
                EventType eventType = reader.nextTag();
                if (eventType == EventType.START_ELEMENT) {
                    QName name = reader.getName();
                    if (checkForTopLevel
                            && !isTopLevel
                            && chunkOptions.containsProperty(name)) {
                        isTopLevel = true;
                    } else if (isTopLevel) {
                        if (types.contains(GlobalObjects.Type.APPEARANCE)
                                && "Appearance".equals(name.getLocalPart())
                                && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                            globalObjects.add(reader.getObject(Appearance.class));
                        } else if (types.contains(GlobalObjects.Type.CITY_OBJECT_GROUP)
                                && "CityObjectGroup".equals(name.getLocalPart())
                                && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                            globalObjects.add(reader.getObject(CityObjectGroup.class));
                        }
                        isTopLevel = false;
                    } else if (types.contains(GlobalObjects.Type.IMPLICIT_GEOMETRY)
                            && "ImplicitGeometry".equals(name.getLocalPart())
                            && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                        ImplicitGeometry implicitGeometry = reader.getObject(ImplicitGeometry.class);
                        if (implicitGeometry.getRelativeGeometry() != null
                                && implicitGeometry.getRelativeGeometry().isSetInlineObject()
                                && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
                            globalObjects.add(implicitGeometry);
                        }
                    }
                }
            }

            return globalObjects;
        } catch (Exception e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }
}
