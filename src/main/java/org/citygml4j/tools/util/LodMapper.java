/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.cityjson.util.lod.DefaultLodMapper;

import java.util.Locale;
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
            return name().toLowerCase(Locale.ROOT);
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
