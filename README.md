# Real-Time Edge Detection

This is a technical project showcasing real-time camera frame processing using **Android + OpenCV (C++) + OpenGL ES + JNI**. The app captures camera frames, applies OpenCV processing in native C++ code, and renders the results in real-time using OpenGL ES.

---

## Features Implemented
- **Camera Feed Integration (Android)**  
  Capture live camera frames using `TextureView` or `SurfaceTexture`.  

- **Frame Processing via OpenCV (C++)**  
  Apply Canny Edge Detection or Grayscale filter via JNI.  

- **Render Output with OpenGL ES**  
  Display processed frames as textures with smooth performance (10–15 FPS minimum).  

- **Web Viewer (TypeScript / WebSockets)**
  - Module to view real-time processed frames on a browser using a lightweight Node.js or local server setup.

- **Bonus Features**  
  - Toggle between raw camera feed and edge-detected output.  
  - FPS counter for monitoring performance.  
  - OpenGL shaders for visual effects (grayscale/invert).  
---

## Architecture Overview
- `/app` – Android code in Java/Kotlin handling camera and UI.  
- `/jni` – Native C++ code for OpenCV processing via JNI.  
- `/gl` – OpenGL ES renderer classes for frame display.
- `/web` - TypeScript web viewer

**Project Flow:**  
1. Camera frames are captured in Android.  
2. Frames are sent to native C++ using JNI.  
3. OpenCV applies edge detection/grayscale filters.  
4. Processed frames are sent back to OpenGL textures for real-time rendering.  

---

## Setup Instructions

### 1. Android Studio & SDK
- Install **Android Studio Bumblebee or later**.  
- Make sure **NDK (Native Development Kit)** is installed via SDK Manager.  
- Ensure **CMake** and **LLDB** are installed.  

### 2. OpenCV Setup
- Download the OpenCV Android SDK from [OpenCV.org](https://opencv.org/releases/).  
- Extract to a known location, e.g., `C:/opencv-android-sdk`.  
- In `CMakeLists.txt`, set the path to the OpenCV SDK `include` and `lib` directories.  

### 3. Building the Project
1. Open the project in Android Studio.  
2. Sync Gradle to ensure dependencies are loaded.  
3. Build the project with `Build > Make Project` or `Shift+F10`.  
4. Run on a physical device (camera access required).  

### 4. Optional Settings
- Adjust camera resolution in `CameraRenderer` for performance tuning.  
- Modify OpenGL shaders in `/gl` for visual effects.  

---

## 📸 Screenshots / GIFs

<table style="width:100%; table-layout:fixed;">
  <tr>
    <td align="center" width="50%">
      <strong>App Home Page</strong><br>
      <img src="phone-feed.jpeg" width="350"/>
    </td>
    <td align="center" width="50%">
      <strong>Raw Camera Feed</strong><br>
      <img src="demo.gif" width="350"/>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Side-by-Side Comparison</strong><br>
      <img src="comparison.jpg" width="350"/>
    </td>
    <td align="center" width="50%">
      <strong>Web Module</strong><br>
      <img src="demo2.gif" width="400"/>
    </td>
  </tr>
</table>

---


## Notes
- All image processing is done in native C++ to ensure real-time performance.  
- Java/Kotlin code is kept minimal, focusing only on camera capture and UI.  
- OpenGL shaders are modular and can be extended for additional effects.  

---

**Enjoy exploring real-time edge detection with Android + OpenCV + OpenGL!**
