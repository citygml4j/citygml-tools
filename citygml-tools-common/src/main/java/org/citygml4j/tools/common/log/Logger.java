/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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
package org.citygml4j.tools.common.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class Logger {
	private static Logger instance = new Logger();

	private LogLevel level = LogLevel.INFO;
	private AtomicInteger warnings = new AtomicInteger(0);
	private AtomicInteger errors = new AtomicInteger(0);

	private Logger() {
		// just to thwart instantiation
	}

	public static Logger getInstance() {
		return instance;
	}

	public void setLogLevel(LogLevel level) {
		this.level = level;
	}

	public LogLevel getLogLevel() {
		return level;
	}

	private String getPrefix(LogLevel level) {
		return "[" +
				LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME) +
				" " +
				level.name() +
				"] ";
	}

	private void log(LogLevel level, String msg) {
		if (this.level.ordinal() >= level.ordinal())
			System.out.println(getPrefix(level) + msg);
	}

	private void log(LogLevel level, String msg, Throwable e) {
		if (this.level.ordinal() >= level.ordinal()) {
			System.out.println(getPrefix(level) + msg);

			do {
				if (e.getMessage() != null)
					log(level, "Cause: " + e.getClass().getName() + ": " + e.getMessage());
			} while ((e = e.getCause()) != null);
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
		warnings.incrementAndGet();
	}

	public void warn(String msg, Throwable e) {
		log(LogLevel.WARN, msg, e);
		warnings.incrementAndGet();
	}

	public void error(String msg) {
		log(LogLevel.ERROR, msg);
		errors.incrementAndGet();
	}

	public void error(String msg, Throwable e) {
		log(LogLevel.ERROR, msg, e);
		errors.incrementAndGet();
	}

	public void logStackTrace(Throwable e) {
		e.printStackTrace(System.err);
	}

	public void print(LogLevel level, String msg) {
		if (this.level.ordinal() >= level.ordinal())
			System.out.println(msg);
	}

	public int getNumberOfErrors() {
		return errors.get();
	}

	public int getNumberOfWarnings() {
		return warnings.get();
	}

}
