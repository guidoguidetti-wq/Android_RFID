const express = require('express');
const router = express.Router();
const rfidController = require('../controllers/rfidController');

// POST /api/rfid/scan - Registra singola scansione RFID
router.post('/scan', rfidController.recordScan);

// POST /api/rfid/batch-scan - Registra batch di scansioni
router.post('/batch-scan', rfidController.recordBatchScan);

// GET /api/rfid/movements - Ottieni movimenti recenti
router.get('/movements', rfidController.getRecentMovements);

// GET /api/rfid/movements/unexpected - Ottieni movimenti inaspettati
router.get('/movements/unexpected', rfidController.getUnexpectedMovements);

// GET /api/rfid/movements/date-range - Ottieni movimenti per range date
router.get('/movements/date-range', rfidController.getMovementsByDateRange);

// GET /api/rfid/movements/:epc - Ottieni storico movimenti per EPC
router.get('/movements/:epc', rfidController.getMovementsByEpc);

module.exports = router;
