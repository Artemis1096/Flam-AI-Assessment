cmake_minimum_required(VERSION 3.22.1)
project(native-lib)

# ----------------------
# C++ setup
# ----------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ----------------------
# Native library sources
# ----------------------
add_library(native-lib SHARED
        native-lib.cpp
        ../gl/CameraRenderer.cpp
)

# ----------------------
# OpenCV configuration
# ----------------------
# Path to OpenCV Android SDK (adjust if needed)
set(OPENCV_ANDROID_SDK "${CMAKE_CURRENT_LIST_DIR}/../OpenCV-android-sdk/native")

set(OpenCV_INCLUDE_DIR "${OPENCV_ANDROID_SDK}/jni/include")
set(OpenCV_LIB_DIR "${OPENCV_ANDROID_SDK}/libs/${ANDROID_ABI}")

# Validate directories
if(NOT EXISTS "${OpenCV_INCLUDE_DIR}")
    message(FATAL_ERROR "OpenCV include directory not found: ${OpenCV_INCLUDE_DIR}")
endif()

if(NOT EXISTS "${OpenCV_LIB_DIR}")
    message(FATAL_ERROR "OpenCV library directory not found: ${OpenCV_LIB_DIR}")
endif()

# Select correct OpenCV shared library
set(OPENCV_LIB_NAME "opencv_java4")
if(EXISTS "${OpenCV_LIB_DIR}/libopencv_java4.so")
    set(OPENCV_LIB_PATH "${OpenCV_LIB_DIR}/libopencv_java4.so")
    message(STATUS "Found OpenCV library: libopencv_java4.so")
elseif(EXISTS "${OpenCV_LIB_DIR}/libopencv_java3.so")
    set(OPENCV_LIB_NAME "opencv_java3")
    set(OPENCV_LIB_PATH "${OpenCV_LIB_DIR}/libopencv_java3.so")
    message(STATUS "Found OpenCV library: libopencv_java3.so")
else()
    message(FATAL_ERROR "No OpenCV library found in: ${OpenCV_LIB_DIR}")
endif()

# ----------------------
# uWebSockets configuration
# ----------------------
# You must manually compile the uSockets and uWebSockets libraries and
# place the resulting .a/.so files in the jniLibs folder for your Android ABI.
# This CMake file will then import and link those precompiled binaries.

add_library(uWebSockets-lib STATIC IMPORTED)
set_target_properties(uWebSockets-lib PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/jniLibs/${ANDROID_ABI}/libuWebSockets.a"
)

add_library(uSockets-lib STATIC IMPORTED)
set_target_properties(uSockets-lib PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/jniLibs/${ANDROID_ABI}/libuSockets.a"
)

# ----------------------
# Include directories
# ----------------------
include_directories("${OpenCV_INCLUDE_DIR}")
include_directories("${CMAKE_CURRENT_LIST_DIR}/../gl")
# Add the uWebSockets and uSockets header paths.
# These folders must exist in your project.
include_directories("${CMAKE_CURRENT_LIST_DIR}/uWebSockets/src")
include_directories("${CMAKE_CURRENT_LIST_DIR}/uWebSockets/uSockets/src")

# ----------------------
# Import prebuilt OpenCV library
# ----------------------
add_library(${OPENCV_LIB_NAME} SHARED IMPORTED)
set_target_properties(${OPENCV_LIB_NAME} PROPERTIES
        IMPORTED_LOCATION "${OPENCV_LIB_PATH}"
)

# ----------------------
# Android system libraries
# ----------------------
find_library(log-lib log)
find_library(android-lib android)
find_library(GLESv2-lib GLESv2)        # OpenGL ES 2.0
find_library(EGL-lib EGL)              # Optional for EGL context

# ----------------------
# Link libraries
# ----------------------
target_link_libraries(native-lib
        uWebSockets-lib
        uSockets-lib
        ${OPENCV_LIB_NAME}
        ${log-lib}
        ${android-lib}
        ${GLESv2-lib}
        ${EGL-lib}
)

# ----------------------
# Status message
# ----------------------
message(STATUS "✅ Successfully linked OpenCV + OpenGL ES for native-lib")