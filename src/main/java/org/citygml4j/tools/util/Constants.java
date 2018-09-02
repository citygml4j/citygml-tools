package org.citygml4j.tools.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path APP_HOME;
    public static final Path WORKING_DIR;

    static {
        String appHomeEnv = System.getenv("APP_HOME");
        if (appHomeEnv == null)
            appHomeEnv = ".";

        String workingDirEnv = System.getenv("WORKING_DIR");
        if (workingDirEnv == null)
            workingDirEnv = ".";

        APP_HOME = Paths.get(appHomeEnv).normalize().toAbsolutePath();
        WORKING_DIR = Paths.get(workingDirEnv).normalize().toAbsolutePath();
    }
}
