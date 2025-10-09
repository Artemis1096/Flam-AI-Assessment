// --- Get References to DOM Elements ---
const canvas = document.getElementById('video-canvas') as HTMLCanvasElement;
const statusEl = document.getElementById('status') as HTMLSpanElement;
const ctx = canvas.getContext('2d');

// --- WebSocket Connection ---
// ❗ IMPORTANT: Find your phone's IP address in its Wi-Fi settings
// and replace the placeholder below.
const PHONE_IP_ADDRESS = '<YOUR_PHONE_IP_ADDRESS>';
const WEBSOCKET_URL = `ws://${PHONE_IP_ADDRESS}:9001`;

const socket = new WebSocket(WEBSOCKET_URL);

// The C++ server sends binary JPEG data. 'blob' is the correct type to handle it.
socket.binaryType = 'blob';

socket.onopen = () => {
console.log('Successfully connected to the WebSocket server!');
  statusEl.textContent = 'Connected ✅';
  statusEl.style.color = '#28a745';
};

socket.onerror = (error) => {
  console.error('WebSocket Error:', error);
  statusEl.textContent = 'Connection Error ❌';
  statusEl.style.color = '#dc3545';
};

socket.onclose = () => {
  console.log('WebSocket connection closed.');
  statusEl.textContent = 'Disconnected';
  statusEl.style.color = '#ffc107';
};

// This function is called every time a new frame arrives from the server.
socket.onmessage = async (event) => {
  // We only process messages that are Blobs (the binary data).
  if (event.data instanceof Blob && ctx) {
    try {
      // Create an ImageBitmap from the blob. This is highly efficient.
      const imageBitmap = await createImageBitmap(event.data);

      // Set the canvas size to match the incoming frame size, but only once.
      if (canvas.width !== imageBitmap.width || canvas.height !== imageBitmap.height) {
        canvas.width = imageBitmap.width;
        canvas.height = imageBitmap.height;
      }

      // Draw the new image frame onto the canvas, replacing the previous one.
      ctx.drawImage(imageBitmap, 0, 0);

    } catch (err) {
      console.error('Error processing frame:', err);
    }
  }
};

