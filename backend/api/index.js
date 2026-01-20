// Vercel serverless function handler
// This file is executed for all routes via rewrites

const path = require('path');

// Load environment variables (Vercel loads them automatically, but this ensures compatibility)
require('dotenv').config({ path: path.join(__dirname, '../.env') });

// Load the Express app using absolute path
const app = require(path.join(__dirname, '../src/server'));

// Export the Express app for Vercel serverless
module.exports = app;
