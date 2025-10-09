#ifndef FRAMESERVER_H
#define FRAMESERVER_H

#include <opencv2/core/mat.hpp>
#include <App.h>
#include <thread>

class FrameServer {
public:
    FrameServer();
    ~FrameServer();

    // Starts the server on a background thread
    void start(int port = 9001);

    // Stops the server
    void stop();

    // Encodes and sends a frame to all connected clients
    void sendFrame(const cv::Mat& frame);

private:
    // The uWS App object
    uWS::App* app = nullptr;
    // The listening socket
    struct us_listen_socket_t* listen_socket = nullptr;
    // The server thread
    std::thread server_thread;
};

#endif //FRAMESERVER_H