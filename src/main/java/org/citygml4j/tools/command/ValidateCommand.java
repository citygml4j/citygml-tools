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

package org.citygml4j.tools.command;

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.LogLevel;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.InputFiles;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import org.xmlobjects.schema.SchemaHandler;
import org.xmlobjects.schema.SchemaHandlerException;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.stream.XMLReaderFactory;
import picocli.CommandLine;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@CommandLine.Command(name = "validate",
        description = "Validates CityGML files against the CityGML XML schemas.")
public class ValidateCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-e", "--suppress-errors"},
            description = "Do not print validation error messages for a concise report.")
    private boolean suppressMessages;

    @CommandLine.Option(names = {"-s", "--schema"}, split = ",", paramLabel = "<URI>",
            description = "One or more files or URLs of additional XML schemas to use for the validation. " +
                    "Note that the official CityGML schemas cannot be replaced.")
    private Set<String> schemas;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles()).find();
        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return CommandLine.ExitCode.OK;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");

        log.debug("Loading default CityGML schemas.");
        SchemaHandler rootHandler;
        try {
            rootHandler = getCityGMLContext().getDefaultSchemaHandler();
        } catch (SchemaHandlerException e) {
            throw new ExecutionException("Failed to load default CityGML schemas.", e);
        }

        if (schemas != null) {
            for (String schema : schemas) {
                try {
                    Path schemaFile = CityGMLTools.WORKING_DIR.resolve(schema).toAbsolutePath();
                    log.debug("Reading additional XML schema from " + schemaFile + ".");
                    rootHandler.parseSchema(schema);
                } catch (Exception e) {
                    throw new ExecutionException("Failed to read XML schema from " + schema + ".", e);
                }
            }
        }

        ValidationErrorHandler errorHandler = new ValidationErrorHandler();
        int invalid = 0;

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Validating file " + inputFile.toAbsolutePath() + ".");

            SchemaHandler schemaHandler = new ValidationSchemaHandler(rootHandler);

            // read more external schemas from schemaLocation attribute of root element
            try (XMLReader reader = XMLReaderFactory.newInstance(getCityGMLContext().getXMLObjects())
                    .withSchemaHandler(schemaHandler)
                    .createReader(inputFile, inputOptions.getEncoding())) {
                reader.nextTag();
            } catch (Exception e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            }

            try {
                SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = schemaFactory.newSchema(schemaHandler.getSchemas());

                Validator validator = schema.newValidator();
                validator.setErrorHandler(errorHandler);
                validator.validate(new StreamSource(inputFile.toFile()));

                if (errorHandler.getErrors() == 0) {
                    log.info("The file is valid.");
                } else {
                    log.warn("The file is invalid. Found " + errorHandler.getErrors() + " error(s).");
                    invalid++;
                }
            } catch (Exception e) {
                throw new ExecutionException("Failed to validate CityGML file.", e);
            } finally {
                errorHandler.reset();
            }
        }

        if (invalid == 0) {
            log.info("Validation complete. All files are valid.");
            return CommandLine.ExitCode.OK;
        } else {
            log.warn("Validation complete. Found " + invalid + " invalid file(s).");
            return 3;
        }
    }

    private class ValidationSchemaHandler extends SchemaHandler {

        ValidationSchemaHandler(SchemaHandler other) {
            copy(other);
        }

        @Override
        public void parseSchema(String namespaceURI, String schemaLocation) throws SchemaHandlerException {
            if (!getTargetNamespaces().contains(namespaceURI)) {
                String uri;
                try {
                    uri = Paths.get(URI.create(schemaLocation)).toAbsolutePath().toString();
                } catch (Exception e) {
                    uri = schemaLocation;
                }

                log.debug("Reading external XML schema from " + uri + ".");
            }

            super.parseSchema(namespaceURI, schemaLocation);
        }
    }

    private class ValidationErrorHandler implements ErrorHandler {
        private String location;
        private int errors;

        int getErrors() {
            return errors;
        }

        void reset() {
            location = null;
            errors = 0;
        }

        @Override
        public void warning(SAXParseException exception) {
            reportError(exception, "Warning", LogLevel.WARN);
        }

        @Override
        public void error(SAXParseException exception) {
            reportError(exception, "Invalid content", LogLevel.ERROR);
        }

        @Override
        public void fatalError(SAXParseException exception) {
            reportError(exception, "Invalid content", LogLevel.ERROR);
        }

        private void reportError(SAXParseException exception, String type, LogLevel level) {
            String location = exception.getLineNumber() + ", " + exception.getColumnNumber();
            if (!location.equals(this.location)) {
                this.location = location;
                errors++;
            }

            if (!suppressMessages) {
                log.log(level, type + " at [" + location + "]: " + exception.getMessage());
            }
        }
    }
}
