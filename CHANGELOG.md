# Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/citygml4j/citygml-tools/compare/v2.0.0..HEAD
[2.0.0]: https://github.com/citygml4j/citygml-tools/releases/tag/v2.0.0
[Before 2.0.0]: https://github.com/citygml4j/citygml-tools/blob/citygml-tools-v1/CHANGES.md