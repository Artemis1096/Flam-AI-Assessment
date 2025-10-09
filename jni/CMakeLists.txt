cmake_minimum_required(VERSION 3.22.1)
project(native-lib)

# ----------------------
# C++ setup
# ----------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ----------------------
# Set a stable location for downloaded dependencies
# ----------------------
set(FETCHCONTENT_BASE_DIR "C:/Android/deps")

# ----------------------
# Fetch uWebSockets and uSockets
# ----------------------
include(FetchContent)

# uSockets dependency
FetchContent_Declare(
        usockets
        GIT_REPOSITORY https://github.com/uNetworking/uSockets.git
        GIT_TAG        v0.8.7
)

# uWebSockets main library
FetchContent_Declare(
        uwebsockets
        GIT_REPOSITORY https://github.com/uNetworking/uWebSockets.git
        GIT_TAG        v20.45.0
)

# Download both dependencies
FetchContent_MakeAvailable(usockets uwebsockets)

# ----------------------
# Define native library
# ----------------------
add_library(native-lib SHARED
        native-lib.cpp
        ../gl/CameraRenderer.cpp
        ../gl/FrameServer.cpp
)

# ----------------------
# Include directories
# ----------------------
target_include_directories(native-lib PRIVATE
        ../gl
        ${uwebsockets_SOURCE_DIR}/src
        ${usockets_SOURCE_DIR}/src
)

# ----------------------------------------------------
# --- SIMPLIFIED AND CORRECTED OPENCV CONFIGURATION ---
# ----------------------------------------------------
set(OPENCV_ANDROID_SDK "${CMAKE_CURRENT_LIST_DIR}/../OpenCV-android-sdk/native")
set(OpenCV_INCLUDE_DIR "${OPENCV_ANDROID_SDK}/jni/include")

# Define the full path to the OpenCV library file directly
set(OpenCV_LIB_PATH "${OPENCV_ANDROID_SDK}/libs/${ANDROID_ABI}/libopencv_java4.so")

# Verify the library file actually exists at that path
if(NOT EXISTS "${OpenCV_LIB_PATH}")
    message(FATAL_ERROR "OpenCV library file not found at: ${OpenCV_LIB_PATH}")
endif()

# Import the prebuilt OpenCV library
add_library(opencv_java SHARED IMPORTED)
set_target_properties(opencv_java PROPERTIES
        IMPORTED_LOCATION "${OpenCV_LIB_PATH}"
        INTERFACE_INCLUDE_DIRECTORIES "${OpenCV_INCLUDE_DIR}"
)

# ----------------------
# Android native dependencies
# ----------------------
find_library(log-lib log)

# ----------------------
# Link all libraries
# ----------------------
target_link_libraries(native-lib
        PRIVATE
        opencv_java # <-- Link to our simplified target name
        ${log-lib}
        EGL
        GLESv2
        uWebSockets
        uSockets
)

# ----------------------
# Message confirmation
# ----------------------
message(STATUS "✅ Successfully configured native-lib with OpenCV + uWebSockets + uSockets")