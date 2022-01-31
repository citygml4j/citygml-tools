# citygml-tools
citygml-tools is a command line utility that bundles several operations for processing CityGML files.

## License
citygml-tools is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
See the `LICENSE` file for more details.

## Latest release
The latest stable release of citygml-tools is 1.4.4.

Download the citygml-tools 1.4.4 release binaries
[here](https://github.com/citygml4j/citygml-tools/releases/download/v1.4.4/citygml-tools-1.4.4.zip). Previous releases
are available from the [releases section](https://github.com/citygml4j/citygml-tools/releases).

## Contributing
* To file bugs found in the software create a GitHub issue.
* To contribute code for fixing filed issues create a pull request with the issue id.
* To propose a new feature create a GitHub issue and open a discussion.

## Using citygml-tools
Download and unzip the latest release or [build](https://github.com/citygml4j/citygml-tools#building) the program from
source. Afterwards, open a shell environment and run the `citygml-tools` script from the program folder to launch the
program.

To show the help message and all available commands of citygml-tools, simply type the following:

    > citygml-tools --help

This will print the following usage information:

```
Usage: citygml-tools [-hV] [--log-file=<file>] [--log-level=<level>]
                     [@<filename>...] COMMAND
Collection of tools for processing CityGML files.
      [@<filename>...]      One or more argument files containing options.
  -h, --help                Show this help message and exit.
      --log-file=<file>     Write log messages to the specified file.
      --log-level=<level>   Log level: error, warn, info, debug (default: info).
  -V, --version             Print version information and exit.
Commands:
  help              Displays help information about the specified command
  validate          Validates CityGML files according to the given subcommand.
  change-height     Changes the height values of city objects by a given offset.
  remove-apps       Removes appearances from city objects.
  move-global-apps  Converts global appearances to local ones.
  clip-textures     Clips texture images to the extent of the target surface.
  filter-lods       Filters the LoD representations of city objects.
  reproject         Reprojects city objects to a new spatial reference system.
  from-cityjson     Converts CityJSON files into CityGML.
  to-cityjson       Converts CityGML files into CityJSON.
```

To get help about a specific command of citygml-tools, enter the following and replace `COMMAND` with the name of
the command you want to learn more about:

    > citygml-tools help COMMAND

The following example shows how to use the `to-cityjson` command to convert a CityGML file into CityJSON:

    > citygml-tools to-cityjson /path/to/your/CityGML.gml

## System requirements
* Java JRE or JDK >= 1.8
  
citygml-tools can be run on any platform providing appropriate Java support. 

## Docker image

citygml-tools is also available as Docker image. You can either build the image yourself using the provided `Dockerfile`
or use a pre-built image from Docker Hub: https://hub.docker.com/r/citygml4j/citygml-tools.

To build the image, clone the repository to your local machine and run the following command from the root of the
repository:

    > docker build -t citygml-tools .

### How to use the image
    
Using citygml-tools via docker is simple:
 
     > docker run --rm citygml-tools
     
 This will show the help message and all available commands of citygml-tools.
 
 The following command mounts a volume and runs the `to-cityjson` command of citygml-tools on all CityGML files 
 in the mounted volume.

    > docker run --rm -u 1000 -v /path/to/your/data:/data citygml-tools to-cityjson /data

Use the `-u` parameter to pass the username or UID of your current host's user to set the correct file permissions on
generated files in the mounted directory.

### Technical details

The citygml-tools image uses [OpenJDK](https://hub.docker.com/_/openjdk) Alpine Linux to keep the resulting images
small. Additionally, it is written as multi-stage image, which means the "JDK image" is only used for building, while
the final application gets wrapped in a smaller "JRE image".

By default, the container process is executed as non-root user. The included entrypoint script allows the image also to
be used in OpenShift environments, where an arbitrary user might be created on container start.

The default working directory inside the container is `/data`.

## Using citygml-tools as library
citygml-tools is not just a CLI program. Most commands are also available as separate JAR libraries. Simply put the
library file from the `lib` folder on your classpath to use the operation in your citygml4j project. The
`citygml-tools-common-<version>.jar` library renders a mandatory dependency for all commands.

The libraries are also available as [Maven](http://maven.apache.org/) artifacts from the
[Maven Central Repository](https://search.maven.org/search?q=org.citygml4j.tools). For example, to add the
`global-app-mover` library for removing global appearances to your project with Maven, add the following code to your
`pom.xml`. You may need to adapt the `global-app-mover` version number.

```xml
<dependency>
  <groupId>org.citygml4j.tools</groupId>
  <artifactId>global-app-mover</artifactId>
  <version>1.4.4</version>
</dependency>
```

Here is how you use `global-app-mover` with your Gradle project:

```gradle
repositories {
  mavenCentral()
}

dependencies {
  compile 'org.citygml4j.tools:global-app-mover:1.4.4'
}
```

Note that all commands, which are not available as separate JAR library, just require a few lines of code with
citygml4j. Check out the source code to see how they are implemented.

## Building
citygml-tools uses [Gradle](https://gradle.org/) as build system. To build the program from source, clone the
repository to your local machine and run the following command from the root of the repository.

    > gradlew installDist
    
The script automatically downloads all required dependencies for building and running citygml-tools. So make sure you
are connected to the internet. The build process runs on all major operating systems and only requires a Java 8 JDK or
higher to run.

If the build was successful, you will find the citygml-tools package under `citygml-tools/build/install`.