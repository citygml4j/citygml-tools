# Changelog

## [Unreleased]
### Fixed
- In a few scenarios, non-random identifiers were created for city objects that had no identifier in the input file.
  This has been fixed for all commands so that automatically generated identifiers are now random UUIDs.
  ([#47](https://github.com/citygml4j/citygml-tools/issues/47))
- Fixed `upgrade` command to correctly create `CityObjectRelation` links between top-level city objects sharing a
  common geometry.

### Changed
- The `--map-lod1-multi-surfaces` option of the `upgrade` command now creates a `GenericThematicSurface` object
  for an entire LoD1 multi-surface, but not for each surface member anymore.

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
  ([#31](https://github.com/citygml4j/citygml-tools/issues/31))
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

[Unreleased]: https://github.com/citygml4j/citygml-tools/compare/v2.1.0..HEAD
[2.1.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.1.0
[2.0.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.0.0
[Before 2.0.0]: https://github.com/citygml4j/citygml-tools/blob/citygml-tools-v1/CHANGES.md