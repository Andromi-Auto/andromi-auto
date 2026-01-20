cmake_minimum_required(VERSION 3.6.0 FATAL_ERROR)
project(hu_jni) # Added project command

include(CheckIncludeFiles)
include(CheckLibraryExists)

if ("${ANDROID_ABI}" MATCHES "x86_64")
    set(OPENSSL_LIBRARIES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/x86_64")
elseif ("${ANDROID_ABI}" MATCHES "x86")
    set(OPENSSL_LIBRARIES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/x86")
elseif ("${ANDROID_ABI}" MATCHES "64")
    set(OPENSSL_LIBRARIES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/arm64-v8a")
else ()
    set(OPENSSL_LIBRARIES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi-v7a")
endif ()

set(OPENSSL_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jni/headers")

message("OPENSSL_LIBRARIES_DIR ${OPENSSL_LIBRARIES_DIR}")
message("OPENSSL_INCLUDE_DIR ${OPENSSL_INCLUDE_DIR}")

link_directories(${OPENSSL_LIBRARIES_DIR})

# Changed to link against shared libraries (.so)
add_library(libcrypto_1_1 SHARED IMPORTED)
set_target_properties(libcrypto_1_1 PROPERTIES IMPORTED_LOCATION
        ${OPENSSL_LIBRARIES_DIR}/libcrypto_1_1.so)

add_library(libssl_1_1 SHARED IMPORTED)
set_target_properties(libssl_1_1 PROPERTIES IMPORTED_LOCATION
        ${OPENSSL_LIBRARIES_DIR}/libssl_1_1.so)

add_library(hu_jni SHARED
        src/main/jni/hu_uti.c
        src/main/jni/hu_ssl.c
        src/main/jni/hu_jni.c)

target_include_directories(hu_jni PUBLIC ${OPENSSL_INCLUDE_DIR})

target_link_libraries(hu_jni log libssl_1_1 libcrypto_1_1 -lc) # Removed -landroid_support

#target_link_libraries(hu_jni log)
#target_link_libraries(hu_jni ${OPENSSL_LIBRARIES})\
#target_link_libraries(hu_jni libssl.so)
#target_link_libraries(hu_jni libcrypto.so)

