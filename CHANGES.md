Change Log
==========

### 1.3.0 - 2019-04-29

##### Additions
* The `to-cityjson` and `from-cityjson` commands now support [CityJSON 1.0](https://www.cityjson.org/specs/1.0.0/). Note that previous versions of CityJSON are no longer supported.

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
