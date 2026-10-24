cmake_minimum_required(VERSION 3.16)
project(PhysicalLinkSensorRegistryTests LANGUAGES CXX)

enable_testing()
add_executable(sensor_registry_tests
    test_sensor_registry.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
)
target_include_directories(sensor_registry_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(sensor_registry_tests PROPERTIES
    CXX_STANDARD 11
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO
)
if(MSVC)
    target_compile_options(sensor_registry_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(sensor_registry_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()

foreach(test_name IN ITEMS
    registration metadata_ownership optional_metadata duplicate_ids invalid_ids
    string_boundaries utf8_metadata updates invalid_values availability
    unknown_sensors sensor_limit independent_sensors stable_views)
    add_test(NAME registry.${test_name} COMMAND sensor_registry_tests ${test_name})
endforeach()

add_executable(capability_serializer_tests
    test_capability_serializer.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/CapabilitySerializer.cpp
)
target_include_directories(capability_serializer_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(capability_serializer_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO
)
if(MSVC)
    target_compile_options(capability_serializer_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(capability_serializer_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS
    empty one_sensor multiple_order escaping control_characters max_sensors
    max_fields capacity_boundaries invalid_buffers determinism preservation
    registry_access)
    add_test(NAME capabilities.${test_name} COMMAND capability_serializer_tests ${test_name})
endforeach()

# Test-only standard-library JSON parser; no firmware dependency.
find_package(Python3 COMPONENTS Interpreter REQUIRED)
add_test(NAME capabilities.json_roundtrip
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/verify_capabilities_json.py
            $<TARGET_FILE:capability_serializer_tests>)

add_executable(legacy_ble_adapter_tests
    test_legacy_ble_adapter.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/LegacyBleAdapter.cpp)
target_include_directories(legacy_ble_adapter_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(legacy_ble_adapter_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(legacy_ble_adapter_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(legacy_ble_adapter_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
add_test(NAME legacy_ble.profile_and_readings COMMAND legacy_ble_adapter_tests)

add_executable(physical_link_tests
    test_physical_link.cpp
    ../../lib/PhysicalLink/src/PhysicalLink.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/LegacyBleAdapter.cpp)
target_include_directories(physical_link_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(physical_link_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(physical_link_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(physical_link_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS registration validation readings failures cleanup order)
    add_test(NAME public_api.${test_name} COMMAND physical_link_tests ${test_name})
endforeach()

add_executable(v2_core_tests
    ../protocol_v2/test_v2.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/V2Codec.cpp
    ../../lib/PhysicalLink/src/V2Metadata.cpp
    ../../lib/PhysicalLink/src/V2SensorState.cpp)
target_include_directories(v2_core_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(v2_core_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(v2_core_tests PRIVATE /W4 /WX /utf-8 /D_CRT_SECURE_NO_WARNINGS)
else()
    target_compile_options(v2_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS numeric corrupt state images metadata)
    add_test(NAME v2.${test_name} COMMAND v2_core_tests ${test_name})
endforeach()

add_test(NAME v2.golden_fixtures
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/../protocol_v2/verify_fixtures.py
            $<TARGET_FILE:v2_core_tests>)

add_executable(metadata_transport_tests
    ../metadata_transport/test_metadata_transport.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/V2Metadata.cpp
    ../../lib/PhysicalLink/src/V2MetadataSession.cpp
    ../../lib/PhysicalLink/src/V2Codec.cpp
    ../../lib/PhysicalLink/src/V2SensorState.cpp
    ../../lib/PhysicalLink/src/ExperimentalV2Metadata.cpp)
target_include_directories(metadata_transport_tests PRIVATE ../../lib/PhysicalLink/src)
target_compile_definitions(metadata_transport_tests PRIVATE PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA=1)
set_target_properties(metadata_transport_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(metadata_transport_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(metadata_transport_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS single pinning commands lifecycle teardown)
    add_test(NAME metadata_transport.${test_name} COMMAND metadata_transport_tests ${test_name})
endforeach()
add_test(NAME metadata_transport.golden_pages
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/../metadata_transport/verify_pages.py
            $<TARGET_FILE:metadata_transport_tests>)

add_executable(metadata_nimble_binding_tests
    ../metadata_transport/test_nimble_binding.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/V2Metadata.cpp
    ../../lib/PhysicalLink/src/V2MetadataSession.cpp
    ../../lib/PhysicalLink/src/V2Codec.cpp
    ../../lib/PhysicalLink/src/V2SensorState.cpp
    ../../lib/PhysicalLink/src/ExperimentalV2Metadata.cpp
    ../../lib/PhysicalLink/src/ExperimentalMetadataNimBLE.cpp)
target_include_directories(metadata_nimble_binding_tests PRIVATE
    ../../lib/PhysicalLink/src ../metadata_transport)
target_compile_definitions(metadata_nimble_binding_tests PRIVATE
    PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA=1 PHYSICAL_LINK_METADATA_NATIVE_DRIVER_TEST=1)
set_target_properties(metadata_nimble_binding_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(metadata_nimble_binding_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(metadata_nimble_binding_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS startup offsets cleanup live live_rate)
    add_test(NAME metadata_binding.${test_name} COMMAND metadata_nimble_binding_tests ${test_name})
endforeach()

add_executable(v2_live_session_tests
    ../metadata_transport/test_live_session.cpp
    ../../lib/PhysicalLink/src/SensorRegistry.cpp
    ../../lib/PhysicalLink/src/V2Metadata.cpp
    ../../lib/PhysicalLink/src/V2MetadataSession.cpp
    ../../lib/PhysicalLink/src/V2Codec.cpp
    ../../lib/PhysicalLink/src/V2SensorState.cpp)
target_include_directories(v2_live_session_tests PRIVATE ../../lib/PhysicalLink/src)
set_target_properties(v2_live_session_tests PROPERTIES
    CXX_STANDARD 11 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
if(MSVC)
    target_compile_options(v2_live_session_tests PRIVATE /W4 /WX /utf-8)
else()
    target_compile_options(v2_live_session_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
endif()
foreach(test_name IN ITEMS initial consistency commands subscription pinning coalescing rate reconnect)
    add_test(NAME v2_live.${test_name} COMMAND v2_live_session_tests ${test_name})
endforeach()
