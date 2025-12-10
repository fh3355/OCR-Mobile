# Sets the minimum version of CMake required to build the native library.
# Using a more modern version is good practice.
cmake_minimum_required(VERSION 3.18.1)

# --- Defining Project and C++ Standard ---
# It's best practice to set the project name and the C++ standard.
# C++17 is a good, modern standard for NDK development.
project(ocr_mobile_native CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)


# --- Find Prebuilt Dependencies (PaddleLite and OpenCV) ---

# Find PaddleLite
# The path is set relative to this CMakeLists.txt file.
set(PADDLELITE_DIR ${CMAKE_SOURCE_DIR}/../../../../libs/PaddleLite)
include_directories(${PADDLELITE_DIR}/cxx/include)
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${PADDLELITE_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so)

# Find OpenCV
# Using the modern find_package method is more robust.
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../../../libs/OpenCV/sdk/native/jni)
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})

# --- NOTE on Compiler Flags ---
# All custom, aggressive, and conflicting CMAKE_CXX_FLAGS have been REMOVED.
# We will let the Android Gradle Plugin apply the default, safe, and optimized
# flags for the selected build type (Debug/Release). This is the standard and
# most stable approach, avoiding issues like stack overflows and floating-point
# inaccuracies from -Ofast and -ffast-math.


# --- Defining Our Native Library ---

add_library(
  # Sets the name of the library.
  Native
  # Sets the library as a shared library.
  SHARED
  # Provides a relative path to your source file(s).
  Native.cc
  pipeline.cc
  utils.cc
  cls_process.cc
  rec_process.cc
  det_process.cc
  db_post_process.cc
  clipper.cpp
)

# --- Linking Dependencies ---

# Find standard Android libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# Link all required libraries to our native library.
target_link_libraries(
  Native
  # Our prebuilt dependencies
  paddle_light_api_shared
  ${OpenCV_LIBS}
  
  # Standard Android libraries
  GLESv2
  EGL
  ${log-lib}
  ${jnigraphics-lib}
)
