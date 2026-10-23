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
