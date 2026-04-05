/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.util.ExternalResourceCopier;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLWriteException;

import java.util.Map;
import java.util.Objects;
/*
public class SubsetContext {
    private final Filter filter;
    private final OutputFile outputFile;
    private final CityGMLChunkWriter writer;
    private final String logPrefix;

    private SubsetContext(String name, Filter filter, OutputFile outputFile, CityGMLChunkWriter writer) {
        this.filter = Objects.requireNonNull(filter, "The filter must not be null.");
        this.outputFile = Objects.requireNonNull(outputFile, "The output file must not be null.");
        this.writer = Objects.requireNonNull(writer, "The CityGML writer must not be null.");
        logPrefix = !name.equals(SubsetCommand.DEFAULT_GROUP_NAME) ? "[group " + name + "] " : null;
    }

    public static SubsetContext of(String name, Filter filter, OutputFile outputFile, CityGMLChunkWriter writer) {
        return new SubsetContext(name, filter, outputFile, writer);
    }

    public OutputFile getOutputFile() {
        return outputFile;
    }

    public boolean isCountWithinLimit() {
        return filter.isCountWithinLimit();
    }

    public Map<String, Integer> getCounter() {
        return filter.getCounter();
    }

    public String format(String message) {
        return logPrefix != null ? logPrefix + message : message;
    }

    public boolean process(AbstractFeature feature, CityGMLReader reader, ExternalResourceCopier resourceCopier) throws ExecutionException {
        if (filter.filter(feature, reader.getName(), reader.getPrefix())) {
            try {
                resourceCopier.process(feature);
                writer.writeMember(feature);
                return true;
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile + ".", e);
            }
        }

        return false;
    }

    public void postprocess(ExternalResourceCopier resourceCopier) throws ExecutionException {
        try {
            filter.postprocess();

            for (CityObjectGroup group : filter.getCityObjectGroups()) {
                resourceCopier.process(group);
                writer.writeMember(group);
            }

            for (Appearance appearance : filter.getAppearances()) {
                resourceCopier.process(appearance);
                writer.writeMember(appearance);
            }
        } catch (CityGMLWriteException e) {
            throw new ExecutionException("Failed to write file " + outputFile + ".", e);
        }
    }

    public void close() throws ExecutionException {
        try {
            writer.close();
        } catch (CityGMLWriteException e) {
            throw new ExecutionException("Failed to close file " + outputFile + ".", e);
        }
    }
}

 */
