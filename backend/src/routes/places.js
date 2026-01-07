const express = require('express');
const router = express.Router();
const placesController = require('../controllers/placesController');

// GET /api/places - Ottieni tutti i places
router.get('/', placesController.getAllPlaces);

// GET /api/places/:id - Ottieni place specifico
router.get('/:id', placesController.getPlaceById);

// POST /api/places - Crea nuovo place
router.post('/', placesController.createPlace);

// PUT /api/places/:id - Aggiorna place
router.put('/:id', placesController.updatePlace);

// DELETE /api/places/:id - Elimina place
router.delete('/:id', placesController.deletePlace);

module.exports = router;
