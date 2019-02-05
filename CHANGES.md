Change Log
==========

### 1.1.0 - tbd

##### Additions
* The `from-cityjson` and `to-cityjson` commands now support CityJSON 0.9.
* The `to-cityjson` command now allows for setting the `"referenceSystem"` metadata property. [#3](https://github.com/citygml4j/citygml-tools/issues/3)
  * With the new option `--epsg=<code>`, users can force an EPSG code to be used for the CityJSON dataset.
  * Alternatively, the operation tries to read the EPSG code from the CityGML file (read more [here](https://github.com/citygml4j/citygml-tools/issues/3)).

##### Fixes
* Fixed output of the `--version` option. [#2](https://github.com/citygml4j/citygml-tools/issues/2)

##### Miscellaneous
* Switched to citygml4j version 2.9.0.
