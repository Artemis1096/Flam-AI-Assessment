#ifndef CAMERARENDERER_H
#define CAMERARENDERER_H

#include <GLES3/gl3.h>
#include <opencv2/core/mat.hpp>

class CameraRenderer {
public:
    CameraRenderer();
    ~CameraRenderer();

    // Call once to set up shaders, program, and texture
    void init();

    // Call when the surface changes (e.g., screen rotation)
    void onSurfaceChanged(int width, int height);

    // Call every frame to draw the processed cv::Mat
    void drawFrame(const cv::Mat& frame);

    // Call to clean up resources
    void destroy();

private:
    GLuint shaderProgram;
    GLuint textureId;
    GLuint vPositionHandle;
    GLuint vTexCoordHandle;

    int viewWidth;
    int viewHeight;

    // Helper function for compiling shaders
    GLuint loadShader(GLenum type, const char* shaderSrc);
};

#endif //CAMERARENDERER_H