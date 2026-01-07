const express = require('express');
const router = express.Router();
const itemsController = require('../controllers/itemsController');

// GET /api/items - Ottieni tutti gli items
router.get('/', itemsController.getAllItems);

// GET /api/items/:epc - Ottieni item specifico per EPC
router.get('/:epc', itemsController.getItemByEpc);

// GET /api/items/place/:placeId - Ottieni items per place
router.get('/place/:placeId', itemsController.getItemsByPlace);

// GET /api/items/zone/:zoneId - Ottieni items per zone
router.get('/zone/:zoneId', itemsController.getItemsByZone);

// GET /api/items/product/:productId - Ottieni items per product
router.get('/product/:productId', itemsController.getItemsByProduct);

// POST /api/items - Crea o aggiorna item
router.post('/', itemsController.upsertItem);

module.exports = router;
