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
package org.citygml4j.tools.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class Logger {
    private static final Logger instance = new Logger();

    private final AtomicInteger warnings = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private LogLevel level = LogLevel.INFO;
    private BufferedWriter writer;

    private Logger() {
    }

    public static Logger getInstance() {
        return instance;
    }

    public int getNumberOfErrors() {
        return errors.get();
    }

    public int getNumberOfWarnings() {
        return warnings.get();
    }

    public Logger setLogLevel(LogLevel level) {
        this.level = level;
        return this;
    }

    public LogLevel getLogLevel() {
        return level;
    }

    public Logger setLogFile(Path logFile) throws IOException {
        writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
        return this;
    }

    public void log(LogLevel level, String msg) {
        count(level);
        if (this.level.ordinal() >= level.ordinal()) {
            msg = getPrefix(level) + msg;
            System.out.println(msg);
            writeToFile(msg);
        }
    }

    public void debug(String msg) {
        log(LogLevel.DEBUG, msg);
    }

    public void info(String msg) {
        log(LogLevel.INFO, msg);
    }

    public void warn(String msg) {
        log(LogLevel.WARN, msg);
    }

    public void warn(String msg, Throwable e) {
        log(LogLevel.WARN, msg, e);
    }

    public void error(String msg) {
        log(LogLevel.ERROR, msg);
    }

    public void error(String msg, Throwable e) {
        log(LogLevel.ERROR, msg, e);
    }

    public void logStackTrace(Throwable e) {
        if (e != null) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer, true));
            log(LogLevel.ERROR, writer.toString());
        }
    }

    public void print(LogLevel level, String msg) {
        if (this.level.ordinal() >= level.ordinal()) {
            System.out.println(msg);
        }
    }

    public void writeToFile(String msg) {
        if (writer != null) {
            try {
                writer.write(msg);
                writer.newLine();
            } catch (IOException e) {
                //
            }
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                //
            }
        }
    }

    private void log(LogLevel level, String msg, Throwable e) {
        log(level, msg);
        if (e != null && this.level.ordinal() >= level.ordinal()) {
            do {
                if (e.getMessage() != null) {
                    log(level, e.getClass().getName() + ": " + e.getMessage());
                }
            } while ((e = e.getCause()) != null);
        }
    }

    private void count(LogLevel level) {
        switch (level) {
            case WARN:
                warnings.incrementAndGet();
                break;
            case ERROR:
                errors.incrementAndGet();
                break;
        }
    }

    private String getPrefix(LogLevel level) {
        return "[" +
                LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME) +
                " " +
                level.name() +
                "] ";
    }
}
