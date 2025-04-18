/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
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

import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSType;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.xmlobjects.gml.util.GMLConstants;
import org.xmlobjects.schema.SchemaHandler;

import javax.xml.namespace.QName;
import java.util.*;

public class SchemaHelper {
    private final Logger log = Logger.getInstance();
    private final SchemaHandler schemaHandler;
    private final Map<String, Set<String>> features = new HashMap<>();
    private final Map<String, Set<String>> geometries = new HashMap<>();
    private final List<QName> featureTypes = new ArrayList<>();
    private final List<QName> geometryTypes = new ArrayList<>();
    private final Set<String> missingSchemas = new HashSet<>();

    private boolean failOnMissingSchema;

    private SchemaHelper(SchemaHandler schemaHandler) {
        this.schemaHandler = schemaHandler;
        featureTypes.add(new QName(GMLConstants.GML_3_2_NAMESPACE, "AbstractFeatureType"));
        featureTypes.add(new QName(GMLConstants.GML_3_1_NAMESPACE, "AbstractFeatureType"));
        geometryTypes.add(new QName(GMLConstants.GML_3_2_NAMESPACE, "AbstractGeometryType"));
        geometryTypes.add(new QName(GMLConstants.GML_3_1_NAMESPACE, "AbstractGeometryType"));
    }

    public static SchemaHelper of(SchemaHandler schemaHandler) {
        return new SchemaHelper(schemaHandler);
    }

    public SchemaHelper failOnMissingSchema(boolean failOnMissingSchema) {
        this.failOnMissingSchema = failOnMissingSchema;
        return this;
    }

    public Set<String> getMissingSchemas() {
        return missingSchemas;
    }

    public Set<String> getAndResetMissingSchemas() {
        Set<String> missingSchemas = new HashSet<>(this.missingSchemas);
        this.missingSchemas.clear();
        return missingSchemas;
    }

    public boolean hasMissingSchemas() {
        return !missingSchemas.isEmpty();
    }

    public boolean isCityModel(QName element) {
        return "CityModel".equals(element.getLocalPart())
                && CityGMLModules.isCityGMLNamespace(element.getNamespaceURI());
    }

    public boolean isFeature(QName element) throws ExecutionException {
        return isDerivedFrom(element, featureTypes, features);
    }

    public boolean isAppearance(QName element) {
        return "Appearance".equals(element.getLocalPart())
                && CityGMLModules.isCityGMLNamespace(element.getNamespaceURI());
    }

    public boolean isGeometry(QName element) throws ExecutionException {
        return isDerivedFrom(element, geometryTypes, geometries);
    }

    public boolean isImplicitGeometry(QName element) {
        return "ImplicitGeometry".equals(element.getLocalPart())
                && CityGMLModules.isCityGMLNamespace(element.getNamespaceURI());
    }

    public boolean isGenericAttribute(QName element) {
        switch (element.getLocalPart().toLowerCase()) {
            case "codeattribute":
            case "dateattribute":
            case "doubleattribute":
            case "genericattributeset":
            case "intattribute":
            case "measureattribute":
            case "stringattribute":
            case "uriattribute":
                return CityGMLModules.isCityGMLNamespace(element.getNamespaceURI());
            default:
                return false;
        }
    }

    public boolean isBoundingShape(QName element) {
        return "boundedBy".equals(element.getLocalPart())
                && CityGMLModules.isGMLNamespace(element.getNamespaceURI());
    }

    private boolean isDerivedFrom(QName element, List<QName> types, Map<String, Set<String>> visited) throws ExecutionException {
        if (visited.getOrDefault(element.getNamespaceURI(), Collections.emptySet()).contains(element.getLocalPart())) {
            return true;
        }

        XSSchemaSet schemas = getSchemas(element.getNamespaceURI());
        if (schemas != null) {
            XSType type = getType(element, schemas);
            if (type != null) {
                if (types.stream().anyMatch(name -> isDerivedFrom(type, name, schemas))) {
                    visited.computeIfAbsent(element.getNamespaceURI(), v -> new HashSet<>()).add(element.getLocalPart());
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isDerivedFrom(XSType type, QName name, XSSchemaSet schemas) {
        XSType parent = schemas.getType(name.getNamespaceURI(), name.getLocalPart());
        return parent != null && type.isDerivedFrom(parent);
    }

    private XSType getType(QName element, XSSchemaSet schemas) {
        return getType(element.getNamespaceURI(), element.getLocalPart(), schemas);
    }

    private XSType getType(String namespaceURI, String localPart, XSSchemaSet schemas) {
        XSElementDecl element = schemas.getElementDecl(namespaceURI, localPart);
        return element != null ? element.getType() : null;
    }

    private XSSchemaSet getSchemas(String namespaceURI) throws ExecutionException {
        XSSchemaSet schemas = schemaHandler.getSchemaSet(namespaceURI);
        if (schemas == null) {
            if (missingSchemas.add(namespaceURI)) {
                String message = "Missing XML schema for target namespace " + namespaceURI + ".";
                if (failOnMissingSchema) {
                    throw new ExecutionException(message);
                } else {
                    log.warn(message);
                }
            }

            return null;
        } else {
            return schemas;
        }
    }
}
