/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public class URLClassLoader extends java.net.URLClassLoader {

    public URLClassLoader(ClassLoader parentLoader) {
        super(new URL[]{}, parentLoader);
    }

    public void addPath(Path path) {
        try {
            addURL(path.toUri().toURL());
        } catch (MalformedURLException e) {
            //
        }
    }
}
