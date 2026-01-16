const express = require('express');
const router = express.Router();
const checklistController = require('../controllers/checklistController');

// GET /api/checklist - Ottieni tutte le checklist (con filtro opzionale ?type=CHI)
router.get('/', checklistController.getAll);

// GET /api/checklist/:id - Ottieni una checklist specifica
router.get('/:id', checklistController.getById);

// POST /api/checklist - Crea una nuova checklist
router.post('/', checklistController.create);

module.exports = router;
