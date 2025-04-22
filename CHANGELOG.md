# Changelog

## [Unreleased]

### Changed
- **Breaking:** Renamed and harmonized CLI options across commands for greater consistency. Existing scripts or
  workflows using old option names may need to be updated.

### Added
- Introduced a new `merge` command to combine multiple CityGML files into a single output file. [#36](https://github.com/citygml4j/citygml-tools/issues/36)
- Added a new `--output` option to all relevant commands, allowing users to specify a target directory for processed
  files. This option is optional and complements the existing behavior of storing output alongside or overwriting
  the input files. When `--output` is used, the input directory structure is preserved in the output directory, and
  nested resource files (e.g., textures, library objects, point clouds, and timeseries files) are also copied to
  maintain local references. Note that copying a large number of files may increase processing time. [#51](https://github.com/citygml4j/citygml-tools/discussions/51)
- Added a `--crs-name` option to the `from-cityjson` command to optionally set the value of the `gml:srsName`
  attribute in the CityGML output. [#57](https://github.com/citygml4j/citygml-tools/issues/57)

## [2.3.2] - 2024-08-15

### Fixed
- Fixed start script for UNIX/Linux and Docker.

## [2.3.1] - 2024-07-15

### Changed
- CityGML `Section` and `Intersection` features are no longer considered by the `to-cityjson` command
  due to missing support in CityJSON.

### Fixed
- `LandUse` features were not converted to CityJSON using the `to-cityjson` command.
- Fixed mapping of closure surfaces to CityJSON. [#54](https://github.com/citygml4j/citygml-tools/issues/54)
- Fixed reading of CityJSON storeys and building units with the `from-cityjson` command.
- Fixed support for the `linux/arm64` architecture in the citygml-tools Docker image.
  [#52](https://github.com/citygml4j/citygml-tools/issues/52)

## [2.3.0] - 2024-01-29

### Added
- The `apply-xslt` command now supports XSLT/XPath 2.0 and 3.0.
- The citygml-tools Docker images are now available as GitHub packages in addition to Docker Hub.

### Changed
- **Breaking:** Java 17 is now the minimum required version for running citygml-tools.
- Improved performance when resolving global references in the `to-cityjson` command.

### Fixed
- Inline appearances of implicit geometries in CityGML 3 are now correctly converted to CityJSONSeq.
- Fixed UTF-16 and UTF-32 encoding for CityJSON.

## [2.2.0] - 2023-11-03

### Added
- Added support for [CityJSON 2.0](https://www.cityjson.org/specs/2.0.0/) to the `to-cityjson` and `from-cityjson`
  commands. [#50](https://github.com/citygml4j/citygml-tools/issues/50)
- Added the `--map-lod0-roof-edge` option to the `upgrade` command. Use this option to convert bldg:lod0RoofEdge
  properties of buildings in your CityGML 2.0/1.0 input files to individual RoofSurface objects having an LoD0 surface
  in CityGML 3.0.
- Added support for geometry templates to the `from-cityjson` command when reading CityJSON files in JSON Lines format.
  The "geometry-templates" property must be placed in the "CityJSON" object on the first line of the file.

### Changed
- **Breaking:** Renamed the `--write-cityjson-features` option to `--json-lines` for the `to-cityjson` command.
- The `--map-lod1-multi-surfaces` option of the `upgrade` command now creates a `GenericThematicSurface` object
  for the entire LoD1 multi-surface rather than each surface member.
- Child city objects that have no geometry after an upgrade to CityGML 3.0 with the `upgrade` command are now deleted.
  Only empty top-level city objects are kept.
- The `to-cityjson` command now creates geometry templates for the "CityJSON" object on the first line and references
  them from the corresponding "CityJSONFeature" objects when the output is written in JSON Lines format. Replacing
  geometry templates with real coordinates is still supported with the `--replace-implicit-geometries` option, but is
  no longer the default behavior.
- The `.jsonl` file extension is used by the `to-cityjson` command when writing CityJSON files in JSON Lines format. 

### Fixed
- In a few scenarios, non-random identifiers were created for city objects that had no identifier in the input file.
  This has been fixed for all commands so that automatically generated identifiers are now random UUIDs.
  [#47](https://github.com/citygml4j/citygml-tools/issues/47)
- Fixed `upgrade` command to correctly create `CityObjectRelation` links between top-level city objects sharing a
  common geometry.
- Both the `to-local-apps` and the `from-cityjson` commands now correctly set XLink references to appearances of
  implicit geometries when writing to CityGML 3.0.

## [2.1.0] - 2023-04-04

### Added
- Added a `--schema` parameter to the `stats` command to load external XML schema files that are not referenced by
  the input files themselves.
- Added a `--fail-on-missing-schema` parameter to the `stats` command to let the operation fail in case elements of
  the input files are associated with a namespace for which no XML schema has been loaded.

### Changed
- **Breaking:** The short option `-s` of the `stats` command for generating an overall statistics over all input files
  has been renamed to `-r`. The option `-s` is now used as shortcut for the newly introduced `--schema` parameter.
  This is also more consistent with other commands.
- The `stats` command does not fail by default anymore in case elements of the input files are associated with a
  namespace for which no XML schema has been loaded. A warning is logged instead and the command returns with `3` as
  exit code.

### Fixed
- Fixed `to-cityjson` command to write correct coordinate values when transforming coordinates to integers.
  [#31](https://github.com/citygml4j/citygml-tools/issues/31)
- The namespaces of the CityGML 2.0/1.0 base profiles were reported to be unsupported.
- Fixed loading of ADE extensions using the `--extensions` option.
- Avoid localization issues when internally converting strings to lower or upper case.
- Symbolic links in paths to input files are followed and `~` is automatically expanded to the user home directory.

## [2.0.0] - 2022-09-09

### Added
- Full support for parsing and writing CityGML 3.0 datasets encoded in GML/XML. Note that the GML/XML encoding of
  CityGML 3.0 is still a draft and has not yet been published as an OGC standard.
- New `upgrade` command that lets you upgrade your existing CityGML 2.0 and 1.0 datasets to CityGML 3.0.
- New `stats` command to generate statistics about the content of your CityGML files.
- New `subset` command to create a subset of top-level city objects based on user-defined filter criteria.
- New `apply-xslt` command to modify your CityGML files based on XSLT transformations.
- The `to-cityjson` and `from-cityjson` commands now support CityJSON 1.1 in addition to CityJSON 1.0. Moreover,
  support for the new `"CityJSONFeature"` object has been added to allow streaming of large CityJSON datasets.
- Added a `--pid-file` option to create a file containing the ID of the citygml-tools process.

### Changed
- The `move-global-apps` command has been renamed to `to-local-apps`.
- The command-line interface has been slightly changed for some commands, for instance, by renaming options. Make sure
  to print the `--help` of each command before using it.
- citygml-tools now requires Java 11 or higher.
- citygml-tools is now based on citygml4j 3.0.

### Removed
- The `xml` subcommand of the `validate` operation has been removed. Just use the `validate` command to perform
  an XML validation of CityGML files.
- The release of separate JAR libraries bundling some citygml-tools functionalities has been discontinued.


## [Before 2.0.0]

The changelog of previous citygml-tools releases before version 2.0 is available
[here](https://github.com/citygml4j/citygml-tools/blob/citygml-tools-v1/CHANGES.md).

[Unreleased]: https://github.com/citygml4j/citygml-tools/compare/v2.3.2..HEAD
[2.3.2]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.3.2
[2.3.1]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.3.1
[2.3.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.3.0
[2.2.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.2.0
[2.1.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.1.0
[2.0.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.0.0
[Before 2.0.0]: https://github.com/citygml4j/citygml-tools/blob/citygml-tools-v1/CHANGES.md