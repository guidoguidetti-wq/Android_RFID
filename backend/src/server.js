require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const bodyParser = require('body-parser');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN }));
app.use(morgan('dev'));
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Routes
const rfidRoutes = require('./routes/rfid');
const itemsRoutes = require('./routes/items');
const placesRoutes = require('./routes/places');
const zonesRoutes = require('./routes/zones');
const productsRoutes = require('./routes/products');
const authRoutes = require('./routes/auth');
const inventoriesRoutes = require('./routes/inventories');

app.use('/api/rfid', rfidRoutes);
app.use('/api/items', itemsRoutes);
app.use('/api/places', placesRoutes);
app.use('/api/zones', zonesRoutes);
app.use('/api/products', productsRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/inventories', inventoriesRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'OK',
    timestamp: new Date().toISOString(),
    database: {
      host: process.env.DB_HOST,
      database: process.env.DB_NAME
    }
  });
});

// API documentation endpoint
app.get('/api', (req, res) => {
  res.json({
    name: 'RFID Backend API',
    version: '2.0.0',
    endpoints: {
      auth: {
        'POST /api/auth/login': 'Autentica utente',
        'POST /api/auth/register': 'Registra nuovo utente',
        'GET /api/auth/validate/:username': 'Verifica se username esiste',
        'GET /api/auth/users': 'Ottieni tutti gli utenti attivi',
        'PUT /api/auth/users/:userId': 'Aggiorna dati utente',
        'DELETE /api/auth/users/:userId': 'Disattiva utente'
      },
      inventories: {
        'GET /api/inventories': 'Tutti gli inventari con filtri',
        'GET /api/inventories/open/:placeId': 'Inventari aperti per place',
        'GET /api/inventories/:invId': 'Dettagli inventario',
        'GET /api/inventories/:invId/items': 'Items di un inventario',
        'GET /api/inventories/:invId/count': 'Conteggio items',
        'GET /api/inventories/:invId/stats': 'Statistiche inventario',
        'POST /api/inventories': 'Crea nuovo inventario',
        'PUT /api/inventories/:invId': 'Aggiorna inventario',
        'PUT /api/inventories/:invId/state': 'Aggiorna stato (open/closed)',
        'POST /api/inventories/:invId/scan': 'Aggiungi scan a inventario',
        'DELETE /api/inventories/:invId': 'Elimina inventario',
        'DELETE /api/inventories/:invId/items/:epc': 'Rimuovi item',
        'DELETE /api/inventories/:invId/items': 'Svuota inventario'
      },
      rfid: {
        'POST /api/rfid/scan': 'Registra singola scansione RFID',
        'POST /api/rfid/batch-scan': 'Registra batch di scansioni',
        'GET /api/rfid/movements': 'Ottieni movimenti recenti',
        'GET /api/rfid/movements/unexpected': 'Ottieni movimenti inaspettati',
        'GET /api/rfid/movements/date-range': 'Ottieni movimenti per range date',
        'GET /api/rfid/movements/:epc': 'Storico movimenti per EPC'
      },
      items: {
        'GET /api/items': 'Tutti gli items',
        'GET /api/items/:epc': 'Item per EPC',
        'GET /api/items/place/:placeId': 'Items per place',
        'GET /api/items/zone/:zoneId': 'Items per zone',
        'GET /api/items/product/:productId': 'Items per product',
        'POST /api/items': 'Crea/aggiorna item'
      },
      places: {
        'GET /api/places': 'Tutti i places',
        'GET /api/places/:id': 'Place specifico',
        'POST /api/places': 'Crea place',
        'PUT /api/places/:id': 'Aggiorna place',
        'DELETE /api/places/:id': 'Elimina place'
      },
      zones: {
        'GET /api/zones': 'Tutte le zones',
        'GET /api/zones/:id': 'Zone specifica',
        'POST /api/zones': 'Crea zone',
        'PUT /api/zones/:id': 'Aggiorna zone',
        'DELETE /api/zones/:id': 'Elimina zone'
      },
      products: {
        'GET /api/products': 'Tutti i products',
        'GET /api/products/labels': 'Labels campi prodotto',
        'GET /api/products/:id': 'Product specifico',
        'POST /api/products': 'Crea product',
        'PUT /api/products/:id': 'Aggiorna product',
        'DELETE /api/products/:id': 'Elimina product'
      }
    }
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

// Error handling
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Internal server error', details: err.message });
});

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
  console.log(`Database: ${process.env.DB_HOST}:${process.env.DB_PORT}/${process.env.DB_NAME}`);
});

module.exports = app;
