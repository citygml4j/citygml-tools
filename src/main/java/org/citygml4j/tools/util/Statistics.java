/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2024 Claus Nagel <claus.nagel@gmail.com>
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.xmlobjects.gml.model.geometry.Envelope;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class Statistics {
    public enum ModuleType {
        CITYGML,
        ADE
    }

    public enum ObjectType {
        FEATURE,
        GEOMETRY,
        APPEARANCE
    }

    private final List<Path> files = new ArrayList<>();
    private final Set<String> cityObjectIds = new LinkedHashSet<>();
    private final Set<CityGMLVersion> versions = new TreeSet<>();
    private final Set<String> referenceSystems = new LinkedHashSet<>();
    private final Map<String, Envelope> extents = new LinkedHashMap<>();
    private final Set<String> missingSchemas = new HashSet<>();
    private final Set<Integer> lods = new TreeSet<>();
    private final Set<String> themes = new TreeSet<>();
    private boolean hasImplicitGeometries;
    private boolean hasTextures;
    private boolean hasMaterials;
    private boolean hasGlobalAppearances;
    private boolean hasNullTheme;
    private final Map<ModuleType, Map<String, String>> modules = new EnumMap<>(ModuleType.class);
    private final Map<ObjectType, Map<String, Integer>> objects = new EnumMap<>(ObjectType.class);
    private final Map<String, String> genericAttributes = new TreeMap<>();
    private final Map<String, FeatureHierarchy> hierarchies = new TreeMap<>();

    private Statistics(Path file) {
        files.add(file.toAbsolutePath());
    }

    public static Statistics of(Path file) {
        return new Statistics(file);
    }

    public Path getFile() {
        return files.get(0);
    }

    public Statistics withCityObjectIds(Set<String> cityObjectIds) {
        if (cityObjectIds != null && !cityObjectIds.isEmpty()) {
            this.cityObjectIds.addAll(cityObjectIds);
        }

        return this;
    }

    public void addVersion(CityGMLVersion version) {
        if (version != null) {
            versions.add(version);
        }
    }

    public void addReferenceSystem(String referenceSystem) {
        if (referenceSystem != null) {
            referenceSystems.add(referenceSystem);
        }
    }

    public Envelope getExtent(String referenceSystem) {
        return extents.computeIfAbsent(referenceSystem, v -> new Envelope());
    }

    public boolean hasValidExtent() {
        return extents.values().stream().anyMatch(Envelope::isValid);
    }

    public void addMissingSchema(String namespaceURI) {
        missingSchemas.add(namespaceURI);
    }

    public boolean hasMissingSchemas() {
        return !missingSchemas.isEmpty();
    }

    public void addLod(int lod) {
        lods.add(lod);
    }

    public void addTheme(String theme) {
        if (theme != null) {
            themes.add(theme);
        } else {
            hasNullTheme = true;
        }
    }

    public void setHasImplicitGeometries(boolean hasImplicitGeometries) {
        this.hasImplicitGeometries = hasImplicitGeometries;
    }

    public void setHasTextures(boolean hasTextures) {
        this.hasTextures = hasTextures;
    }

    public void setHasMaterials(boolean hasMaterials) {
        this.hasMaterials = hasMaterials;
    }

    public void setHasGlobalAppearances(boolean hasGlobalAppearances) {
        this.hasGlobalAppearances = hasGlobalAppearances;
    }

    public void addModule(ModuleType type, String prefix, String namespaceURI) {
        modules.computeIfAbsent(type, v -> new TreeMap<>()).put(prefix, namespaceURI);
    }

    public void addModule(String prefix, String namespaceURI) {
        ModuleType type = CityGMLModules.isCityGMLNamespace(namespaceURI)
                || CityGMLModules.isGMLNamespace(namespaceURI) ?
                ModuleType.CITYGML :
                ModuleType.ADE;

        addModule(type, prefix, namespaceURI);
    }

    public boolean hasModules(ModuleType type) {
        return modules.containsKey(type);
    }

    public void addObject(ObjectType type, String name, int count) {
        objects.computeIfAbsent(type, v -> new TreeMap<>()).merge(name, count, Integer::sum);
    }

    public void addObject(ObjectType type, String name) {
        addObject(type, name, 1);
    }

    public void addFeature(String name) {
        addObject(ObjectType.FEATURE, name);
    }

    public void addGeometry(String name) {
        addObject(ObjectType.GEOMETRY, name);
    }

    public void addAppearance(String name) {
        addObject(ObjectType.APPEARANCE, name);
    }

    public boolean hasObjects(ObjectType type) {
        return objects.containsKey(type);
    }

    public void addGenericAttribute(String name, String type) {
        genericAttributes.put(name, type);
    }

    public void addFeatureHierarchy(List<String> hierarchy) {
        if (!hierarchy.isEmpty()) {
            FeatureHierarchy node = hierarchies.computeIfAbsent(hierarchy.get(0), v -> new FeatureHierarchy());
            for (int i = 1; i < hierarchy.size(); i++) {
                node = node.getChildren().computeIfAbsent(hierarchy.get(i), v -> new FeatureHierarchy());
            }

            node.incrementCount();
        }
    }

    public void merge(Statistics other) {
        files.addAll(other.files);
        cityObjectIds.addAll(other.cityObjectIds);
        versions.addAll(other.versions);
        referenceSystems.addAll(other.referenceSystems);
        missingSchemas.addAll(other.missingSchemas);
        lods.addAll(other.lods);
        themes.addAll(other.themes);
        hasImplicitGeometries = other.hasImplicitGeometries || hasImplicitGeometries;
        hasTextures = other.hasTextures || hasTextures;
        hasMaterials = other.hasMaterials || hasMaterials;
        hasGlobalAppearances = other.hasGlobalAppearances || hasGlobalAppearances;
        hasNullTheme = other.hasNullTheme || hasNullTheme;

        for (Map.Entry<String, Envelope> entry : other.extents.entrySet()) {
            getExtent(entry.getKey()).include(entry.getValue());
        }

        for (Map.Entry<ModuleType, Map<String, String>> entry : other.modules.entrySet()) {
            entry.getValue().forEach((prefix, namespaceURI) -> addModule(entry.getKey(), prefix, namespaceURI));
        }

        for (Map.Entry<ObjectType, Map<String, Integer>> entry : other.objects.entrySet()) {
            entry.getValue().forEach((name, count) -> addObject(entry.getKey(), name, count));
        }

        other.genericAttributes.forEach(this::addGenericAttribute);
        other.hierarchies.forEach((name, hierarchy) -> mergeHierarchies(name, hierarchy, hierarchies));
    }

    private void mergeHierarchies(String name, FeatureHierarchy hierarchy, Map<String, FeatureHierarchy> hierarchies) {
        FeatureHierarchy target = hierarchies.get(name);
        if (target == null) {
            hierarchies.put(name, hierarchy);
        } else {
            target.incrementCount(hierarchy.getCount());
            for (Map.Entry<String, FeatureHierarchy> entry : hierarchy.getChildren().entrySet()) {
                mergeHierarchies(entry.getKey(), entry.getValue(), target.getChildren());
            }
        }
    }

    public ObjectNode toJson(ObjectMapper objectMapper) {
        ObjectNode statistics = objectMapper.createObjectNode();

        if (files.size() == 1) {
            statistics.put("file", files.get(0).toString());
        } else if (!files.isEmpty()) {
            ArrayNode files = statistics.putArray("files");
            this.files.stream().map(Path::toString).forEach(files::add);
        }

        if (!cityObjectIds.isEmpty()) {
            ArrayNode cityObjectIds = statistics.putArray("cityObjectIds");
            this.cityObjectIds.forEach(cityObjectIds::add);
        }

        if (versions.size() == 1) {
            statistics.put("version", versions.iterator().next().toString());
        } else if (!versions.isEmpty()) {
            ArrayNode versions = statistics.putArray("versions");
            this.versions.stream().map(CityGMLVersion::toString).forEach(versions::add);
        }

        if (!referenceSystems.isEmpty()) {
            ArrayNode referenceSystems = statistics.putArray("referenceSystems");
            this.referenceSystems.forEach(referenceSystems::add);
        }

        if (referenceSystems.size() == 1) {
            toJson(extents.get(referenceSystems.iterator().next()), "extent", statistics);
        } else if (!extents.isEmpty()) {
            ObjectNode extents = statistics.putObject("extents");
            for (Map.Entry<String, Envelope> entry : this.extents.entrySet()) {
                toJson(entry.getValue(), entry.getKey(), extents);
            }
        }

        if (hasMissingSchemas()) {
            ArrayNode missingSchemas = statistics.putArray("missingSchemas");
            this.missingSchemas.forEach(missingSchemas::add);
        }

        if (hasObjects(ObjectType.GEOMETRY)) {
            ObjectNode geometry = statistics.putObject("geometry");
            if (!lods.isEmpty()) {
                ArrayNode lods = geometry.putArray("lods");
                this.lods.forEach(lods::add);
            }

            geometry.put("hasImplicitGeometries", hasImplicitGeometries);
        }

        if (hasObjects(ObjectType.FEATURE)) {
            ObjectNode appearance = statistics.putObject("appearance");
            appearance.put("hasTextures", hasTextures);
            appearance.put("hasMaterials", hasMaterials);

            if (hasTextures || hasMaterials) {
                appearance.put("hasGlobalAppearances", hasGlobalAppearances);
            }

            if (!themes.isEmpty() || hasNullTheme) {
                ArrayNode themes = appearance.putArray("themes");
                this.themes.forEach(themes::add);
                if (hasNullTheme) {
                    themes.add(NullNode.getInstance());
                }
            }
        }

        if (hasModules(ModuleType.CITYGML)) {
            ObjectNode modules = statistics.putObject("modules");
            this.modules.getOrDefault(ModuleType.CITYGML, Collections.emptyMap()).forEach(modules::put);
        }

        if (hasModules(ModuleType.ADE)) {
            ObjectNode ades = statistics.putObject("ades");
            modules.getOrDefault(ModuleType.ADE, Collections.emptyMap()).forEach(ades::put);
        }

        if (hasObjects(ObjectType.FEATURE)) {
            ObjectNode featureCount = statistics.putObject("objectCount");
            objects.getOrDefault(ObjectType.FEATURE, Collections.emptyMap()).forEach(featureCount::put);
        }

        if (hasObjects(ObjectType.GEOMETRY)) {
            ObjectNode geometryCount = statistics.putObject("geometryCount");
            objects.getOrDefault(ObjectType.GEOMETRY, Collections.emptyMap()).forEach(geometryCount::put);
        }

        if (hasObjects(ObjectType.APPEARANCE)) {
            ObjectNode appearanceCount = statistics.putObject("appearanceCount");
            objects.getOrDefault(ObjectType.APPEARANCE, Collections.emptyMap()).forEach(appearanceCount::put);
        }

        if (!genericAttributes.isEmpty()) {
            ObjectNode genericAttributes = statistics.putObject("genericAttributes");
            this.genericAttributes.forEach(genericAttributes::put);
        }

        if (!hierarchies.isEmpty()) {
            ArrayNode hierarchies = statistics.putArray("hierarchies");
            addHierarchies(this.hierarchies, hierarchies);
        }

        return statistics;
    }

    private void toJson(Envelope extent, String propertyName, ObjectNode node) {
        if (extent != null && extent.isValid()) {
            ArrayNode target = node.putArray(propertyName);
            extent.toCoordinateList3D().forEach(target::add);
        }
    }

    private void addHierarchies(Map<String, FeatureHierarchy> hierarchies, ArrayNode target) {
        for (Map.Entry<String, FeatureHierarchy> entry : hierarchies.entrySet()) {
            ObjectNode hierarchy = target.addObject();
            hierarchy.put(entry.getKey(), entry.getValue().getCount());
            if (!entry.getValue().getChildren().isEmpty()) {
                addHierarchies(entry.getValue().getChildren(), hierarchy.putArray("children"));
            }
        }
    }

    public void print(ObjectMapper objectMapper, Consumer<String> printer) {
        ObjectNode statistics = toJson(objectMapper);

        printer.accept("=== CityGML content statistics ===");

        if (statistics.has("file")) {
            printer.accept("File: " + statistics.get("file"));
        } else if (statistics.has("files")) {
            printer.accept("Files: " + statistics.get("files"));
        }

        if (statistics.has("cityObjectIds")) {
            printer.accept("City objects: " + statistics.get("cityObjectIds"));
        }

        if (statistics.has("version")) {
            printer.accept("CityGML version: " + statistics.get("version"));
        } else if (statistics.has("versions")) {
            printer.accept("CityGML versions: " + statistics.get("versions"));
        }

        if (statistics.has("referenceSystems")) {
            printer.accept("Reference systems: " + statistics.get("referenceSystems"));
        }

        if (statistics.has("extent")) {
            printer.accept("Extent: " + statistics.get("extent"));
        } else if (statistics.path("extents").isObject()) {
            ObjectNode extents = (ObjectNode) statistics.get("extents");
            extents.fields().forEachRemaining(field -> printer.accept("Extent (\"" + field.getKey() + "\"): " +
                    field.getValue()));
        }

        if (statistics.path("missingSchemas").isArray()) {
            printer.accept("Missing schemas: " + statistics.get("missingSchemas"));
        }

        if (statistics.path("geometry").isObject()) {
            ObjectNode geometry = (ObjectNode) statistics.get("geometry");
            printer.accept("=== Geometry =====================");
            if (geometry.has("lods")) {
                printer.accept("LoDs: " + geometry.get("lods"));
            }

            printer.accept("hasImplicitGeometries: " + geometry.path("hasImplicitGeometries").asBoolean());
        }

        if (statistics.path("appearance").isObject()) {
            ObjectNode appearance = (ObjectNode) statistics.get("appearance");
            printer.accept("=== Appearance ===================");
            printer.accept("hasTextures: " + appearance.path("hasTextures").asBoolean());
            printer.accept("hasMaterials: " + appearance.path("hasMaterials").asBoolean());

            if (appearance.path("hasTextures").asBoolean() || appearance.path("hasMaterials").asBoolean()) {
                printer.accept("hasGlobalAppearances: " + appearance.path("hasGlobalAppearances").asBoolean());
            }

            if (appearance.has("themes")) {
                printer.accept("Themes: " + appearance.get("themes"));
            }
        }

        if (statistics.path("modules").isObject()) {
            ObjectNode modules = (ObjectNode) statistics.get("modules");
            printer.accept("=== Modules ======================");
            modules.fields().forEachRemaining(field -> printer.accept(field.getKey() + ": " + field.getValue()));
        }

        if (statistics.path("ades").isObject()) {
            ObjectNode ades = (ObjectNode) statistics.get("ades");
            printer.accept("=== ADEs =========================");
            ades.fields().forEachRemaining(field -> printer.accept(field.getKey() + ": " + field.getValue()));
        }

        if (statistics.path("objectCount").isObject()) {
            ObjectNode objects = (ObjectNode) statistics.get("objectCount");
            printer.accept("=== Object count =================");
            objects.fields().forEachRemaining(field -> printer.accept(field.getKey() + ": " + field.getValue()));
        }

        if (statistics.path("geometryCount").isObject()) {
            ObjectNode geometries = (ObjectNode) statistics.get("geometryCount");
            printer.accept("=== Geometry count ===============");
            geometries.fields().forEachRemaining(field -> printer.accept(field.getKey() + ": " + field.getValue()));
        }

        if (statistics.path("appearanceCount").isObject()) {
            ObjectNode appearances = (ObjectNode) statistics.get("appearanceCount");
            printer.accept("=== Appearance count =============");
            appearances.fields().forEachRemaining(field -> printer.accept(field.getKey() + ": " + field.getValue()));
        }

        if (statistics.path("genericAttributes").isObject()) {
            ObjectNode attributes = (ObjectNode) statistics.get("genericAttributes");
            printer.accept("=== Generic attributes ===========");
            attributes.fields().forEachRemaining(field -> printer.accept("\"" + (field.getKey()) + "\" (" +
                    field.getValue().asText() + ")"));
        }

        if (statistics.path("hierarchies").isArray()) {
            ArrayNode hierarchies = (ArrayNode) statistics.get("hierarchies");
            printer.accept("=== Aggregated hierarchies =======");
            hierarchies.elements().forEachRemaining(hierarchy -> printHierarchies(hierarchy, 0, printer));
        }

        printer.accept("==================================");
    }

    private void printHierarchies(JsonNode hierarchy, int level, Consumer<String> printer) {
        if (hierarchy.fields().hasNext()) {
            String indent = level > 0 ? "    ".repeat(level) : "";
            Map.Entry<String, JsonNode> object = hierarchy.fields().next();
            printer.accept(indent + "|-- " + object.getKey() + " (" + object.getValue() + ")");
            hierarchy.path("children").elements().forEachRemaining(child -> printHierarchies(child, level + 1, printer));
        }
    }

    private static class FeatureHierarchy {
        private int count;
        private final Map<String, FeatureHierarchy> children = new TreeMap<>();

        public int getCount() {
            return count;
        }

        void incrementCount(int count) {
            this.count += count;
        }

        void incrementCount() {
            incrementCount(1);
        }

        public Map<String, FeatureHierarchy> getChildren() {
            return children;
        }
    }
}
