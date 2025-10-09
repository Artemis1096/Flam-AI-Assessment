// This function runs after the HTML document has loaded
document.addEventListener('DOMContentLoaded', () => {
    // Get the HTML elements by their ID
    const imageElement = document.getElementById('processedFrame') as HTMLImageElement;
    const resolutionElement = document.getElementById('resolution') as HTMLSpanElement;
    const fpsElement = document.getElementById('fps') as HTMLSpanElement;

    // --- WebSocket Connection Logic ---

    // ❗️ IMPORTANT: Replace this with your Android device's local IP address
    const deviceIp = "192.168.1.10"; // Find this in your phone's Wi-Fi settings
    const socketUrl = `ws://${deviceIp}:9001`;

    const connect = () => {
        // Create a new WebSocket connection to the server on your Android device
        const ws = new WebSocket(socketUrl);

        // Called when the connection is successfully opened
        ws.onopen = () => {
            console.log("✅ Connected to Android WebSocket server!");
            if (fpsElement) fpsElement.innerText = "Connected";
        };

        // This is the core real-time part: called every time a new frame arrives
        ws.onmessage = (event) => {
            // event.data contains the Base64 string of the processed frame
            // We prepend the data URI scheme to make it a valid image source
            if (imageElement) {
                imageElement.src = "data:image/jpeg;base64," + event.data;
            }
        };

        // Called when the connection is closed
        ws.onclose = () => {
            console.log("🔌 Disconnected. Retrying in 3 seconds...");
            if (fpsElement) fpsElement.innerText = "Disconnected";
            // Attempt to reconnect automatically after a 3-second delay
            setTimeout(connect, 3000);
        };

        // Called if an error occurs
        ws.onerror = (error) => {
            console.error("WebSocket Error:", error);
            ws.close(); // This will trigger the onclose event and the reconnect logic
        };
    };

    // Make the first connection attempt
    connect();
});