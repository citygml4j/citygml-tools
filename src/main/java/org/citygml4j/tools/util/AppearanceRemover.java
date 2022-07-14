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

import org.citygml4j.core.model.appearance.AbstractSurfaceDataProperty;
import org.citygml4j.core.model.appearance.AbstractTexture;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.appearance.X3DMaterial;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.model.Child;

import java.util.*;

public class AppearanceRemover {
    public static final String NULL_THEME = "null";
    private final AppearanceProcessor appearanceProcessor = new AppearanceProcessor();
    private final Map<String, Integer> counter = new TreeMap<>();

    private Set<String> themes;
    private boolean onlyTextures;
    private boolean onlyMaterials;
    private boolean keep;

    private AppearanceRemover() {
    }

    public static AppearanceRemover newInstance() {
        return new AppearanceRemover();
    }

    public AppearanceRemover withThemes(Set<String> themes) {
        this.themes = themes;
        return this;
    }

    public AppearanceRemover onlyTextures(boolean onlyTextures) {
        this.onlyTextures = onlyTextures;
        return this;
    }

    public AppearanceRemover onlyMaterials(boolean onlyMaterials) {
        this.onlyMaterials = onlyMaterials;
        return this;
    }

    public boolean removeAppearance(AbstractFeature feature) {
        keep = true;
        feature.accept(appearanceProcessor);
        return !(feature instanceof Appearance) || keep;
    }

    public Map<String, Integer> getCounter() {
        return counter;
    }

    public void reset() {
        counter.clear();
    }

    private class AppearanceProcessor extends ObjectWalker {

        @Override
        public void visit(Appearance appearance) {
            if (shouldRemove(appearance)) {
                Child property = appearance.getParent();
                if (property == null) {
                    keep = false;
                } else {
                    Child parent = property.getParent();
                    if (parent instanceof GMLObject) {
                        ((GMLObject) parent).unsetProperty(property, true);
                    }
                }
            }
        }

        private boolean shouldRemove(Appearance appearance) {
            if (themes == null
                    || (appearance.getTheme() == null && themes.contains(NULL_THEME))
                    || themes.contains(appearance.getTheme())) {
                if (!onlyTextures && !onlyMaterials) {
                    count(appearance, counter);
                    return true;
                }

                Class<?> target = onlyMaterials ? X3DMaterial.class : AbstractTexture.class;
                List<AbstractSurfaceDataProperty> deleteList = new ArrayList<>();
                appearance.getSurfaceData().stream()
                        .filter(property -> target.isInstance(property.getObject()))
                        .forEach(property -> {
                            count(property.getObject(), counter);
                            deleteList.add(property);
                        });

                appearance.getSurfaceData().removeAll(deleteList);
                if (!appearance.isSetSurfaceData()) {
                    count(appearance, counter);
                    return true;
                }
            }

            return false;
        }

        private void count(Object object, Map<String, Integer> counter) {
            counter.merge(object.getClass().getSimpleName(), 1, Integer::sum);
        }
    }
}
