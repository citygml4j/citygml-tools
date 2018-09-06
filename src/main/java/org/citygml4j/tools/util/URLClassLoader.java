package org.citygml4j.tools.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public class URLClassLoader extends java.net.URLClassLoader {

    public URLClassLoader(ClassLoader parentLoader) {
        super(new URL[]{}, parentLoader);
    }

    public URLClassLoader() {
        this(Util.class.getClassLoader());
    }

    public void addPath(Path path) {
        try {
            super.addURL(path.toUri().toURL());
        } catch (MalformedURLException e) {
            //
        }
    }
}
