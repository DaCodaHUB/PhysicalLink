# Protocol version 2 codec regression tests

The folder/fixture names retain `v2` to identify the frozen wire version. Source
components now have production names (`ProtocolCodec`, `SensorState`, `Metadata`)
under `physical_link::protocol`; there is no alternate production transport.

`fixtures/numeric-v2.json` is unchanged. Both native tests and Android's
`PhysicalLinkProtocolTest` / `LiveCodecTest` use its golden vectors. The JSON
oracle and page oracle compare encoded production output, not copied constants.

Run the complete native suite from `firmware/soil-monitor`:

```sh
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
```

Records remain 20 bytes, images 16-byte headers plus ordered records (max 336),
metadata at most 8192 bytes, pages at most 512 bytes with 16-byte headers. No C++
structs are sent raw. Tests cover binary64 values, unavailable zero bits, corrupt
lengths/reserved bits/channels, sequences, acquisition ages, clock wrap/exhaustion,
registration bounds, deterministic metadata, paging boundaries, and split UTF-8.
See the [transport guide](../metadata_transport/LIVE_TRANSPORT.md) for session rules.
