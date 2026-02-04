const { Pool } = require('pg');

// Configurazione SSL per Neon
const sslConfig = process.env.DB_SSL === 'require'
  ? { rejectUnauthorized: false }
  : false;

console.log('Database config:', {
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  ssl: !!sslConfig
});

const pool = new Pool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  ssl: sslConfig,
  // Configurazione per Vercel serverless
  max: 1, // Massimo 1 connessione per lambda
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000,
});

pool.on('connect', () => {
  console.log('Connected to PostgreSQL database');
});

pool.on('error', (err) => {
  console.error('PostgreSQL connection error:', err);
  // Non uscire su Vercel, lascia che la funzione gestisca l'errore
  if (process.env.NODE_ENV !== 'production') {
    process.exit(-1);
  }
});

module.exports = pool;
