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

package org.citygml4j.tools.textureclipper;

public class TextureClippingException extends Exception {
	private static final long serialVersionUID = -3716015045363231263L;

	public TextureClippingException() {
		super();
	}

	public TextureClippingException(String message) {
		super(message);
	}

	public TextureClippingException(Throwable cause) {
		super(cause);
	}

	public TextureClippingException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
