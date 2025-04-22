![build](https://img.shields.io/github/actions/workflow/status/citygml4j/citygml-tools/citygml-tools-build.yml?logo=Gradle)
![edge](https://img.shields.io/github/actions/workflow/status/citygml4j/citygml-tools/docker-build-and-push-edge.yml?label=edge&logo=Docker&logoColor=white)
![release](https://img.shields.io/github/v/release/citygml4j/citygml-tools?display_name=tag)

# citygml-tools

citygml-tools is a command line utility that bundles several operations for processing
[CityGML](https://www.ogc.org/standards/citygml) files.

## License

citygml-tools is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
See the `LICENSE` file for more details.

## Latest release

The latest stable release of citygml-tools is 2.4.0.

Download the latest citygml-tools release as ZIP package
[here](https://github.com/citygml4j/citygml-tools/releases/latest). Previous releases are available from the
[releases section](https://github.com/citygml4j/citygml-tools/releases).

## Contributing

* To file bugs found in the software create a GitHub issue.
* To contribute code for fixing filed issues create a pull request with the issue id.
* To propose a new feature create a GitHub issue and open a discussion.

## Using citygml-tools

Download and unzip the latest release or [build](https://github.com/citygml4j/citygml-tools#building) the program from
source. Afterwards, open a shell environment and run the `citygml-tools` script from the program folder to launch the
program.

To show the help message and all available commands of citygml-tools, type the following:

    > citygml-tools --help

This will print the following usage information:

```
Usage: citygml-tools [-hV] [--extensions=<dir>] [-L=<level>]
                     [--log-file=<file>] [--pid-file=<file>] [@<filename>...]
                     [COMMAND]
Collection of tools for processing CityGML files.
      [@<filename>...]      One or more argument files containing options.
  -L, --log-level=<level>   Log level: error, warn, info, debug (default: info).
      --log-file=<file>     Write log messages to this file.
      --pid-file=<file>     Create a file containing the process ID.
      --extensions=<dir>    Load extensions from this directory.
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.
Commands:
  help           Display help information about the specified command.
  stats          Generate statistics about the content of CityGML files.
  validate       Validate CityGML files against the CityGML XML schemas.
  apply-xslt     Transform city objects based on XSLT stylesheets.
  change-height  Change the height values of city objects by a given offset.
  remove-apps    Remove appearances from city objects.
  to-local-apps  Convert global appearances into local ones.
  clip-textures  Clip texture images to the extent of the target surface.
  merge          Merge multiple CityGML files into a single file.
  subset         Create a subset of city objects based on filter criteria.
  filter-lods    Filter LoD representations of city objects.
  reproject      Reproject city objects to a new coordinate reference system.
  from-cityjson  Convert CityJSON files into CityGML format.
  to-cityjson    Convert CityGML files into CityJSON format.
  upgrade        Upgrade CityGML files to version 3.0.
```

To get help about a specific command of citygml-tools, enter the following and replace `COMMAND` with the name of
the command you want to learn more about:

    > citygml-tools help COMMAND

The following example shows how to use the `stats` command to generate and print statistics about the content
of the specified CityGML file:

    > citygml-tools stats /path/to/your/city.gml

## Supported CityGML versions

You can process CityGML 3.0, 2.0, and 1.0 files with citygml-tools. The `upgrade` command provides an
easy way to convert your existing CityGML 2.0 and 1.0 datasets into the latest version 3.0 of CityGML.

The `from-cityjson` and `to-cityjson` commands support [CityJSON](https://www.cityjson.org/) 2.0, 1.1, and 1.0 files.

## System requirements

* Java 17 or higher

citygml-tools can be run on any platform providing appropriate Java support.

## Docker

citygml-tools is also available as Docker image. You can either build the image yourself using the provided `Dockerfile`
or use a pre-built image from [Docker Hub](https://hub.docker.com/r/citygml4j/citygml-tools) or GitHub packages.
The pre-built images support the most common architectures `(amd64, arm64)`.

To build the image, clone the repository to your local machine and run the following command from the root of the
repository:

    > docker build -t citygml-tools .

An official image can be pulled from Docker Hub as shown below.

    > docker pull citygml4j/citygml-tools:TAG

Replace the `TAG` label with the version of citygml-tools you want to use such as `v2.3.2`. The `latest` tag
refers to the latest stable release and is also the _default value_ if no tag is specified. If you want to pull the
most recent unreleased snapshot of citygml-tools, use `edge` as tag.

#### How to use the image

Using citygml-tools via Docker is simple:

     > docker run --rm citygml-tools

This will show the help message and all available commands of citygml-tools.

The following command mounts a local directory at `/data` using the `-v` parameter and runs the `to-cityjson` command
of citygml-tools to convert all CityGML files in the mounted volume into CityJSON.

    > docker run --rm -u 1000 -v /path/to/your/data:/data citygml-tools to-cityjson *.gml

Note that `/data` is the default working directory inside the container. Relative paths to input files like
in the above example are automatically resolved against `/data` by citygml-tools. If you mount your local directory at
a different path inside the container, you must specify the full path to your input files, of course. 

Use the optional `-u` parameter to pass the username or UID of your current host's user to set the correct file
permissions on generated files in the mounted directory.

## Building

citygml-tools uses [Gradle](https://gradle.org/) as build system. To build the program from source, clone the
repository to your local machine and run the following command from the root of the repository.

    > gradlew installDist

The script automatically downloads all required dependencies for building and running citygml-tools. So make sure you
are connected to the internet.

If the build was successful, you will find the citygml-tools package under `citygml-tools/build/install`.