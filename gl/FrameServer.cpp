#include "FrameServer.h"
#include <opencv2/imgcodecs.hpp>
#include "Base64.h"
#include <Loop.h> // <-- This line fixes the 'undeclared identifier uS' error

// Define an empty struct to use as per-socket data.
struct PerSocketData {};

void FrameServer::start(int port) {
    server_thread = std::thread([this, port]() {
        app = new uWS::App();

        app->ws<PerSocketData>("/*", {
                        /* Settings */
                        .open = [](auto *ws) {
                            ws->subscribe("broadcast");
                        },
                })
                .listen(port, [this, port](auto *socket) {
                    this->listen_socket = socket;
                    if (listen_socket) {
                        // Server started successfully
                    }
                }).run();

        // Server stopped
        delete app;
        app = nullptr;
    });
}

void FrameServer::sendFrame(const cv::Mat& frame) {
    if (!app || frame.empty()) return;

    std::vector<uchar> buffer;
    std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, 50};
    cv::imencode(".jpg", frame, buffer, params);

    std::string base64_string = base64_encode(buffer.data(), buffer.size());

    if(app) {
        app->publish("broadcast", base64_string, uWS::OpCode::TEXT);
    }
}

FrameServer::FrameServer() : app(nullptr), listen_socket(nullptr) {}

FrameServer::~FrameServer() {
    stop();
}

void FrameServer::stop() {
    if (server_thread.joinable()) {
        // Get the event loop and call defer on it to schedule the shutdown
        uS::Loop::get()->defer([this]() {
            if (listen_socket) {
                us_listen_socket_close(0, listen_socket);
                listen_socket = nullptr;
            }
        });

        // Wait for the server thread to finish
        server_thread.join();
    }
}