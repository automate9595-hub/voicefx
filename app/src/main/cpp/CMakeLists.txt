cmake_minimum_required(VERSION 3.22.1)
project(voicefx LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe (AAudio/OpenSL ES) llega como paquete Prefab desde Gradle: com.google.oboe:oboe
find_package(oboe REQUIRED CONFIG)

add_library(voicefx SHARED jni.cpp audio/OboeEngine.cpp)
target_include_directories(voicefx PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
# Sin -ffast-math: el motor depende de comparaciones con NaN/Inf para protegerse.
target_compile_options(voicefx PRIVATE -O3 -Wall -fno-math-errno)
target_link_libraries(voicefx oboe::oboe log android)
