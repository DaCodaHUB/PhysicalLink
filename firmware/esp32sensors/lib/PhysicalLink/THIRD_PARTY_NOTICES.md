# Dependency notices

This source archive contains PhysicalLink and its example sketches. It does not
vendor NimBLE-Arduino, Arduino-ESP32, ESP-IDF, their binaries, or their license
files. Installing/building resolves those dependencies separately. These notices
do not replace the upstream licenses or license PhysicalLink itself.

- **NimBLE-Arduino 2.5.1**, by h2zero and upstream contributors: its root
  [LICENSE](https://github.com/h2zero/NimBLE-Arduino/blob/2.5.1/LICENSE) is Apache-2.0.
  The dependency also incorporates upstream components with their own notices;
  retain applicable notices and license texts when redistributing them.
- **Arduino-ESP32**, provided by `espressif32@7.1.3`: the tested installed framework
  package is `4.20017.260907+sha.dcc1105b`, with manifest license
  `LGPL-2.1-or-later`. See the
  [upstream core](https://github.com/espressif/arduino-esp32) and its component
  licenses, including ESP-IDF components. Framework components are not relicensed
  by the PhysicalLink project.
- The real-sensor helper implements BME280 compensation described in the
  [Bosch datasheet](https://www.bosch-sensortec.com/products/environmental-sensors/humidity-sensors-bme280/).
  No Adafruit or Bosch software library is bundled. The copyright holder must
  confirm source provenance for the helper as well as the library before release.

For redistribution of compiled firmware or dependency sources, preserve the
applicable upstream copyright/license/NOTICE files, identify modifications where
required, and provide the materials required by the LGPL for the actual linked
build (including relinking requirements where applicable). The source-only
PhysicalLink archive inspection is not an audit of every transitive binary
component. Do not treat this notice as clearance for arbitrary firmware binaries.
