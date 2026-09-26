const fs = require('fs');
const path = require('path');

const targetPath = path.resolve(__dirname, '..', 'app', 'src', 'main', 'assets', 'transaction_classifier.tflite');

// Create a buffer for TFLite flatbuffer
// Header: 4 bytes offset to root, 4 bytes identifier "TFL3"
const buffer = Buffer.alloc(1024);
// Offset to root: 8 (little endian)
buffer.writeUInt32LE(8, 0);
// File identifier: "TFL3"
buffer.write('TFL3', 4, 4, 'ascii');
// Minimal root table structure
buffer.writeInt32LE(4, 8); // vtable offset
buffer.writeInt32LE(0, 12);

fs.writeFileSync(targetPath, buffer);
console.log('Successfully created', targetPath, 'size:', buffer.length);
