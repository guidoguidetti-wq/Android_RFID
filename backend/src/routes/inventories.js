const express = require('express');
const router = express.Router();
const inventoriesController = require('../controllers/inventoriesController');

/**
 * Inventories Routes
 * Base path: /api/inventories
 */

// GET /api/inventories - Ottieni tutti gli inventari con filtri
router.get('/', inventoriesController.getAll);

// GET /api/inventories/open/:placeId - Ottieni inventari aperti per place
router.get('/open/:placeId', inventoriesController.getOpenByPlace);

// GET /api/inventories/:invId - Ottieni dettagli inventario
router.get('/:invId', inventoriesController.getById);

// GET /api/inventories/:invId/items - Ottieni items di un inventario
router.get('/:invId/items', inventoriesController.getItems);

// GET /api/inventories/:invId/items-details - Ottieni items con dettagli prodotto (JOIN)
router.get('/:invId/items-details', inventoriesController.getItemsWithDetails);

// GET /api/inventories/:invId/count - Ottieni conteggio items
router.get('/:invId/count', inventoriesController.getItemsCount);

// GET /api/inventories/:invId/stats - Ottieni statistiche inventario
router.get('/:invId/stats', inventoriesController.getStats);

// POST /api/inventories - Crea nuovo inventario
router.post('/', inventoriesController.create);

// PUT /api/inventories/:invId - Aggiorna dati inventario
router.put('/:invId', inventoriesController.update);

// PUT /api/inventories/:invId/state - Aggiorna stato inventario
router.put('/:invId/state', inventoriesController.updateState);

// POST /api/inventories/:invId/scan - Aggiungi scan a inventario
router.post('/:invId/scan', inventoriesController.addScan);

// DELETE /api/inventories/:invId - Elimina inventario
router.delete('/:invId', inventoriesController.deleteInventory);

// DELETE /api/inventories/:invId/items/:epc - Rimuovi item da inventario
router.delete('/:invId/items/:epc', inventoriesController.removeItem);

// DELETE /api/inventories/:invId/items - Svuota inventario
router.delete('/:invId/items', inventoriesController.clearItems);

module.exports = router;
