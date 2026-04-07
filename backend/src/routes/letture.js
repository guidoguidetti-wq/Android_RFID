const express = require('express');
const router = express.Router();
const lettureController = require('../controllers/lettureController');

router.post('/init',       lettureController.initSession);
router.get('/counters',    lettureController.getCounters);
router.get('/items',       lettureController.getItems);
router.post('/batch-scan', lettureController.batchScan);
router.delete('/clear',    lettureController.clearSession);
router.post('/commit',     lettureController.commitSession);

module.exports = router;
