const express = require('express');
const router = express.Router();
const itemsController = require('../controllers/itemsController');

// GET /api/items - Ottieni tutti gli items
router.get('/', itemsController.getAllItems);

// GET /api/items/:epc - Ottieni item specifico per EPC
router.get('/:epc', itemsController.getItemByEpc);

// GET /api/items/:epc/history - Ottieni storico movimenti per EPC
router.get('/:epc/history', itemsController.getItemHistory);

// GET /api/items/:epc/movements/count - Conta movimenti per EPC
router.get('/:epc/movements/count', itemsController.getItemMovementCount);

// POST /api/items/movements/count-batch - Conta movimenti per lista EPCs
router.post('/movements/count-batch', itemsController.getItemsMovementCountBatch);

// GET /api/items/place/:placeId - Ottieni items per place
router.get('/place/:placeId', itemsController.getItemsByPlace);

// GET /api/items/zone/:zoneId - Ottieni items per zone
router.get('/zone/:zoneId', itemsController.getItemsByZone);

// GET /api/items/product/:productId - Ottieni items per product
router.get('/product/:productId', itemsController.getItemsByProduct);

// POST /api/items - Crea o aggiorna item
router.post('/', itemsController.upsertItem);

module.exports = router;
