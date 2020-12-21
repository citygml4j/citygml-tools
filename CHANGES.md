Change Log
==========

### 1.4.2 - 2020-12-21

##### Additions
* Added the `--lenient-transform` option to the `reproject` command to transform coordinates even when there is no
information available for a datum shift.

##### Fixes
* Reworked the `from-cityjson` command to use less main memory. It now uses the new chunking CityJSON reader introduced
with citygml4j 2.11.2. [#18](https://github.com/citygml4j/citygml-tools/issues/18)
* Fixed a NPE when the log file should be created in the current working directory.

### 1.4.1 - 2020-09-04

##### Fixes
* Fixed missing geometry of PlantCover features when converting from CityJSON to CityGML using the `from-cityjson`
command. [#17](https://github.com/citygml4j/citygml-tools/issues/17)

##### Miscellaneous
* Updated to citygml4j version 2.11.1.

### 1.4.0 - 2020-07-14

##### Additions
* citygml-tools is now available as Docker image. You can either build the image yourself using the provided `Dockerfile`
or use a pre-built image from Docker Hub: https://hub.docker.com/r/clausnagel/citygml-tools. Thanks and kudos to [@kdeininger](https://github.com/kdeininger) for this great contribution. [#10](https://github.com/citygml4j/citygml-tools/pull/10)
* Added the `validate` command to validate CityGML files. The command expects a subcommand. With this release, the only
available subcommand is `xml` to perform an XML validation against the CityGML schemas, but more validation options are
on the roadmap. [#11](https://github.com/citygml4j/citygml-tools/pull/11)
* Added support for specifying the encoding of input and output files. [#15](https://github.com/citygml4j/citygml-tools/issues/15)
* Added support for writing log messages to a file. [#12](https://github.com/citygml4j/citygml-tools/issues/12)
* Added support for abbreviated options and subcommands. Users can now specify the initial letter(s) of the first component
and optionally of one or more subsequent components of an option or subcommand name. "Components" are separated by the dash
`-` character.
* Logging options are now global and available from all commands.

##### Breaking changes
* The start scripts to run citygml-tools are now located in the root program folder and not in the `bin`
subfolder anymore.
* The `--log` option has been renamed to `--log-level`.

##### Fixes
* Fixed memory leak in `to-cityjson` command when running the command on multiple files. [#14](https://github.com/citygml4j/citygml-tools/issues/14)
* Fixed a bug in `to-cityjson` command that led to linearly increasing file sizes when running the command on multiple files. [#14](https://github.com/citygml4j/citygml-tools/issues/14)

##### Miscellaneous
* Updated to citygml4j version 2.11.0.
* All subcommand JAR libraries now share the same version number.

### 1.3.2 - 2019-11-01

##### Additions
* Added option `--remove-duplicate-child-geometries` to the `to-cityjson` command to avoid redundant geometries. [#7](https://github.com/citygml4j/citygml-tools/issues/7)

##### Fixes
* Fixed possible endless loop in `to-cityjson` command. [#8](https://github.com/citygml4j/citygml-tools/issues/8)
* Fixed creation of geometries for CityJSON city objects with nested boundary surfaces. [#7](https://github.com/citygml4j/citygml-tools/issues/7)

##### Miscellaneous
* Updated to citygml4j version 2.10.4.
* Updated commons-imaging for `texture-clipper`.
* Bumped version of `texture-clipper` to 1.1.1.

### 1.3.1 - 2019-08-11

##### Fixes
* Fixed `IndexOutOfBoundsException` if `gml:Envelope` is declared but empty. [#5](https://github.com/citygml4j/citygml-tools/issues/5)

##### Miscellaneous
* Updated to citygml4j version 2.10.2.

### 1.3.0 - 2019-04-29

##### Additions
* The `from-cityjson` and `to-cityjson` commands now support [CityJSON 1.0](https://www.cityjson.org/specs/1.0.0/). Note that previous versions of CityJSON are no longer supported.

### 1.2.0 - 2019-04-18

##### Additions
* Added new `remove-apps`, `change-height`, `reproject`, and `filter-lods` commands
  * `remove-apps`: Removes appearances from city objects.
  * `change-height`: Changes the height values of city objects by a given offset.
  * `reproject`: Reprojects city objects to a new spatial reference system.
  * `filter-lods`: Filters the LoD representations of city objects.
* Overwrite output files per default for every command
* Added `--clean-output` option to `clip-textures` command
* Added `--pretty-print` option to `to-cityjson` command

##### Fixes
* Output files created in a previous run of a command are not processed when re-running the command.
* Catch and log exceptions while reading CityJSON files.
* Minor bugfixes and improvements.

##### Miscellaneous
* Switched to citygml4j version 2.9.2.

### 1.1.0 - 2019-02-06

##### Additions
* The `from-cityjson` and `to-cityjson` commands now support CityJSON version 0.9.
* The `to-cityjson` command now allows for setting the `"referenceSystem"` metadata property. [#3](https://github.com/citygml4j/citygml-tools/issues/3)
  * With the new option `--epsg=<code>`, users can force an EPSG code to be used for the CityJSON dataset.
  * Alternatively, the operation tries to read the EPSG code from the CityGML file (read more [here](https://github.com/citygml4j/citygml-tools/issues/3)).

##### Fixes
* Fixed output of the `--version` option. [#2](https://github.com/citygml4j/citygml-tools/issues/2)

##### Miscellaneous
* Switched to citygml4j version 2.9.0.
