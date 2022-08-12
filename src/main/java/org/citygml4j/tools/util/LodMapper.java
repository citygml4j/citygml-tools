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

import org.citygml4j.cityjson.util.lod.DefaultLodMapper;

import java.util.Map;
import java.util.Set;

public class LodMapper implements org.citygml4j.cityjson.util.lod.LodMapper {
    private final DefaultLodMapper lodMapper = new DefaultLodMapper();
    private Map<Integer, Double> mappings;
    private Set<Double> lods;

    public enum Mode {
        MAXIMUM,
        MINIMUM;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    private LodMapper() {
    }

    public static LodMapper newInstance() {
        return new LodMapper();
    }

    public LodMapper withMode(Mode mode) {
        lodMapper.withMappingStrategy(mode == Mode.MINIMUM ?
                DefaultLodMapper.Strategy.MINIMUM_LOD :
                DefaultLodMapper.Strategy.MAXIMUM_LOD);
        return this;
    }

    public LodMapper withMapping(Map<Integer, Double> mappings) {
        this.mappings = mappings;
        return this;
    }

    @Override
    public void buildMapping(Set<Double> lods) {
        this.lods = lods;
        lodMapper.buildMapping(lods);
    }

    @Override
    public double getMappingFor(int lod) {
        if (mappings == null) {
            return lodMapper.getMappingFor(lod);
        } else {
            double mapping = mappings.getOrDefault(lod, -Double.MAX_VALUE);
            return lods.contains(mapping) ?
                    mapping :
                    lodMapper.getMappingFor(lod);
        }
    }
}
