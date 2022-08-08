package org.citygml4j.tools.util;

import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSType;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.xmlobjects.gml.util.GMLConstants;
import org.xmlobjects.schema.SchemaHandler;

import javax.xml.namespace.QName;
import java.util.*;

public class SchemaHelper {
    private final SchemaHandler schemaHandler;
    private final Map<String, Set<String>> features = new HashMap<>();
    private final Map<String, Set<String>> geometries = new HashMap<>();
    private final List<QName> featureTypes = new ArrayList<>();
    private final List<QName> geometryTypes = new ArrayList<>();

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
        XSType type = getType(element, schemas);
        if (type != null) {
            if (types.stream().anyMatch(name -> isDerivedFrom(type, name, schemas))) {
                visited.computeIfAbsent(element.getNamespaceURI(), v -> new HashSet<>()).add(element.getLocalPart());
                return true;
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
            throw new ExecutionException("Missing XML schema for target namespace " + namespaceURI + ".");
        } else {
            return schemas;
        }
    }
}
