const express = require('express');
const router = express.Router();
const zonesController = require('../controllers/zonesController');

// GET /api/zones - Ottieni tutte le zones
router.get('/', zonesController.getAllZones);

// GET /api/zones/:id - Ottieni zone specifica
router.get('/:id', zonesController.getZoneById);

// POST /api/zones - Crea nuova zone
router.post('/', zonesController.createZone);

// PUT /api/zones/:id - Aggiorna zone
router.put('/:id', zonesController.updateZone);

// DELETE /api/zones/:id - Elimina zone
router.delete('/:id', zonesController.deleteZone);

module.exports = router;
