# citygml-tools
citygml-tools is a command line utility that bundles several operations for processing
CityGML files.

## License
citygml-tools is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0). See the `LICENSE` file for more details.

## Latest release
The latest stable release of citygml-tools is 1.3.2.

Download the citygml-tools 1.3.2 release binaries [here](https://github.com/citygml4j/citygml-tools/releases/download/v1.3.2/citygml-tools-1.3.2.zip). Previous releases are available from the [releases section](https://github.com/citygml4j/citygml-tools/releases).

## Contributing
* To file bugs found in the software create a GitHub issue.
* To contribute code for fixing filed issues create a pull request with the issue id.
* To propose a new feature create a GitHub issue and open a discussion.

## Using citygml-tools
Download and unzip the latest release or build the program from source. Afterwards, open a shell environment and execute the `citygml-tools` script from the `bin` folder to launch the program.

Use the following command to list the available operations.

```
> citygml-tools --help
Usage: citygml-tools [-hV] [--log=<level>] COMMAND
Collection of tools for processing CityGML files.
      --log=<level>   Log level: error, warn, info, debug (default: info).
  -h, --help          Show this help message and exit.
  -V, --version       Print version information and exit.
Commands:
  help              Displays help information about the specified command
  change-height     Changes the height values of city objects by a given offset.
  remove-apps       Removes appearances from city objects.
  move-global-apps  Converts global appearances to local ones.
  clip-textures     Clips texture images to the extent of the target surface.
  filter-lods       Filters the LoD representations of city objects.
  reproject         Reprojects city objects to a new spatial reference system.
  from-cityjson     Converts CityJSON files into CityGML.
  to-cityjson       Converts CityGML files into CityJSON.
```
To display information about how to use a specific command, type the
following:

    > citygml-tools help COMMAND

## System requirements
* Java JRE or JDK >= 1.8
  
citygml-tools can be run on any platform providing appropriate Java support. 

## Using citygml-tools as library
citygml-tools is not just a CLI program. Most operations are also available as separate JAR libraries. Simply put the library file from the `lib` folder on your classpath to use the operation in your citygml4j project. The `citygml-tools-common-<version>.jar` library renders a mandatory dependency for all operations.

The libraries are also available as [Maven](http://maven.apache.org/) artifacts from the [Maven Central Repository](https://search.maven.org/search?q=org.citygml4j.tools) and from [JCenter](https://bintray.com/bintray/jcenter). For example, to add the `global-app-mover` library for removing global appearances to your project with Maven, add the following code to your `pom.xml`. You may need to adapt the `global-app-mover` version number.

```xml
<dependency>
  <groupId>org.citygml4j.tools</groupId>
  <artifactId>global-app-mover</artifactId>
  <version>1.1.0</version>
</dependency>
```

Here is how you use `global-app-mover` with your Gradle project:

```gradle
repositories {
  mavenCentral()
}

dependencies {
  compile 'org.citygml4j.tools:global-app-mover:1.1.0'
}
```

Note that all operations, which are not available as separate JAR library, just require a few lines of code with citygml4j. Check out the source code to see how they are implemented.

## Building
citygml-tools uses [Gradle](https://gradle.org/) as build system. To build the program from source, clone the repository to your local machine and run the following command from the root of the repository. 

    > gradlew installDist
    
The script automatically downloads all required dependencies for building and running citygml-tools. So make sure you are connected to the internet. The build process runs on all major operating systems and only requires a Java 8 JDK or higher to run.

If the build was successful, you will find the citygml-tools package under `citygml-tools/build/install`.
