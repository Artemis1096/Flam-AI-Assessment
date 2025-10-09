#include "CameraRenderer.h"
#include <opencv2/imgproc.hpp>
#include <string> // You'll need to load shader strings from assets

// NOTE: You need a way to load the shader files from your assets folder.
// This is typically done by passing the JNIEnv* from Java down
// or by using the Android Asset Manager C API.
// For now, we'll use placeholder strings.

const char* VERTEX_SHADER = R"glsl(
    attribute vec4 a_Position;
    attribute vec2 a_TexCoord;
    varying vec2 v_TexCoord;
    void main() {
      gl_Position = a_Position;
      v_TexCoord = a_TexCoord;
    }
)glsl";

const char* FRAGMENT_SHADER = R"glsl(
    precision mediump float;
    varying vec2 v_TexCoord;
    uniform sampler2D u_Texture;
    void main() {
      gl_FragColor = texture2D(u_Texture, v_TexCoord);
    }
)glsl";


CameraRenderer::CameraRenderer() : shaderProgram(0), textureId(0) {}
CameraRenderer::~CameraRenderer() { destroy(); }

GLuint CameraRenderer::loadShader(GLenum type, const char* shaderSrc) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;
    glShaderSource(shader, 1, &shaderSrc, NULL);
    glCompileShader(shader);
    // ... (Add error checking for compilation) ...
    return shader;
}

void CameraRenderer::init() {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

    shaderProgram = glCreateProgram();
    glAttachShader(shaderProgram, vertexShader);
    glAttachShader(shaderProgram, fragmentShader);
    glLinkProgram(shaderProgram);
    // ... (Add error checking for linking) ...

    vPositionHandle = glGetAttribLocation(shaderProgram, "a_Position");
    vTexCoordHandle = glGetAttribLocation(shaderProgram, "a_TexCoord");

    // Generate and configure the texture
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void CameraRenderer::onSurfaceChanged(int width, int height) {
    viewWidth = width;
    viewHeight = height;
    glViewport(0, 0, viewWidth, viewHeight);
}

void CameraRenderer::drawFrame(const cv::Mat& frame) {
    if (frame.empty() || shaderProgram == 0) {
        return;
    }

    // This is a common and critical step!
    // Canny output is 1-channel (grayscale). OpenGL needs RGBA.
    // We must convert it before uploading.
    cv::Mat displayFrame;
    if (frame.channels() == 1) {
        cv::cvtColor(frame, displayFrame, cv::COLOR_GRAY2RGBA);
    } else if (frame.channels() == 3) {
        cv::cvtColor(frame, displayFrame, cv::COLOR_BGR2RGBA);
    } else {
        displayFrame = frame;
    }

    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(shaderProgram);

    // Define a quad that fills the screen
    static const GLfloat vertices[] = {
            -1.0f, -1.0f,  // Bottom Left
            1.0f, -1.0f,  // Bottom Right
            -1.0f,  1.0f,  // Top Left
            1.0f,  1.0f   // Top Right
    };
    static const GLfloat texCoords[] = {
            0.0f, 1.0f,   // Bottom Left
            1.0f, 1.0f,   // Bottom Right
            0.0f, 0.0f,   // Top Left
            1.0f, 0.0f    // Top Right
    };

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    // Upload the cv::Mat data to the texture
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, displayFrame.cols, displayFrame.rows, 0, GL_RGBA, GL_UNSIGNED_BYTE, displayFrame.data);

    glVertexAttribPointer(vPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glVertexAttribPointer(vTexCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

    glEnableVertexAttribArray(vPositionHandle);
    glEnableVertexAttribArray(vTexCoordHandle);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(vPositionHandle);
    glDisableVertexAttribArray(vTexCoordHandle);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void CameraRenderer::destroy() {
    if (shaderProgram > 0) glDeleteProgram(shaderProgram);
    if (textureId > 0) glDeleteTextures(1, &textureId);
    shaderProgram = 0;
    textureId = 0;
}