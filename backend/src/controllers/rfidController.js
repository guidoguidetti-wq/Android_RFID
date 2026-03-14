const Item = require('../models/Item');
const Movement = require('../models/Movement');
const pool = require('../db/config');

// Registra una scansione RFID
exports.recordScan = async (req, res) => {
  const {
    epc,
    tid = null,
    placeId = null,
    zoneId = null,
    productId = null,
    rssi = null,
    readsCount = 1,
    antenna = null,
    user = null,
    reader = process.env.READER_ID || 'RFD8500',
    unexpected = false,
    notes = null
  } = req.body;

  try {
    // Validazione EPC
    if (!epc) {
      return res.status(400).json({ error: 'EPC is required' });
    }

    // Upsert item (crea o aggiorna)
    const item = await Item.upsert({
      epc,
      tid,
      placeId,
      zoneId,
      productId
    });

    // Registra movimento
    const movement = await Movement.create({
      epc,
      placeId,
      zoneId,
      unexpected,
      readsCount,
      rssiAvg: rssi,
      user,
      reader,
      antenna,
      notes
    });

    res.json({
      success: true,
      item,
      movement
    });
  } catch (error) {
    console.error('Error recording scan:', error);
    res.status(500).json({ error: 'Failed to record scan', details: error.message });
  }
};

// Registra batch di scansioni (per performance)
exports.recordBatchScan = async (req, res) => {
  const { tags, placeId, zoneId, user, reader } = req.body;

  if (!Array.isArray(tags) || tags.length === 0) {
    return res.status(400).json({ error: 'Tags array is required' });
  }

  try {
    const results = [];

    for (const tag of tags) {
      try {
        const item = await Item.upsert({
          epc: tag.epc,
          tid: tag.tid || null,
          placeId,
          zoneId,
          productId: tag.productId || null
        });

        const movement = await Movement.create({
          epc: tag.epc,
          placeId,
          zoneId,
          unexpected: tag.unexpected || false,
          readsCount: tag.readsCount || 1,
          rssiAvg: tag.rssi || null,
          user,
          reader: reader || process.env.READER_ID || 'RFD8500',
          antenna: tag.antenna || null
        });

        results.push({ epc: tag.epc, success: true, item, movement });
      } catch (error) {
        console.error(`Error processing tag ${tag.epc}:`, error);
        results.push({ epc: tag.epc, success: false, error: error.message });
      }
    }

    const successCount = results.filter(r => r.success).length;

    res.json({
      success: true,
      total: tags.length,
      processed: successCount,
      failed: tags.length - successCount,
      results
    });
  } catch (error) {
    console.error('Error recording batch scan:', error);
    res.status(500).json({ error: 'Failed to record batch scan', details: error.message });
  }
};

// Ottieni storico movimenti per EPC
exports.getMovementsByEpc = async (req, res) => {
  const { epc } = req.params;
  const limit = parseInt(req.query.limit) || 100;

  try {
    const movements = await Movement.findByEpc(epc, limit);
    res.json(movements);
  } catch (error) {
    console.error('Error fetching movements:', error);
    res.status(500).json({ error: 'Failed to fetch movements' });
  }
};

// Ottieni movimenti recenti
exports.getRecentMovements = async (req, res) => {
  const limit = parseInt(req.query.limit) || 100;
  const offset = parseInt(req.query.offset) || 0;

  try {
    const movements = await Movement.findRecent(limit, offset);
    res.json(movements);
  } catch (error) {
    console.error('Error fetching recent movements:', error);
    res.status(500).json({ error: 'Failed to fetch recent movements' });
  }
};

// Ottieni movimenti inaspettati
exports.getUnexpectedMovements = async (req, res) => {
  const limit = parseInt(req.query.limit) || 50;

  try {
    const movements = await Movement.findUnexpected(limit);
    res.json(movements);
  } catch (error) {
    console.error('Error fetching unexpected movements:', error);
    res.status(500).json({ error: 'Failed to fetch unexpected movements' });
  }
};

// Trasferisci tag: aggiorna Items + crea Movements per ogni EPC
exports.transfer = async (req, res) => {
  const { place_id, zone_id, epcs, mov_ref = null, mov_user = null } = req.body;

  if (!place_id || !zone_id || !Array.isArray(epcs) || epcs.length === 0) {
    return res.status(400).json({ error: 'place_id, zone_id and epcs are required' });
  }

  try {
    const placeCheck = await pool.query('SELECT place_id FROM "Places" WHERE place_id = $1', [place_id]);
    if (placeCheck.rowCount === 0) {
      return res.status(400).json({ error: `Place "${place_id}" non trovato` });
    }
    const zoneCheck = await pool.query('SELECT zone_id FROM "Zones" WHERE zone_id = $1', [zone_id]);
    if (zoneCheck.rowCount === 0) {
      return res.status(400).json({ error: `Zone "${zone_id}" non trovata` });
    }

    for (const epc of epcs) {
      await pool.query(
        `INSERT INTO "Items" (item_id, place_last, zone_last, date_lastseen, date_creation)
         VALUES ($1, $2, $3, NOW(), NOW())
         ON CONFLICT (item_id) DO UPDATE SET
           place_last = $2,
           zone_last = $3,
           date_lastseen = NOW()`,
        [epc, place_id, zone_id]
      );
      await pool.query(
        `INSERT INTO "Movements" (mov_epc, mov_dest_place, mov_dest_zone, mov_timestamp, mov_reader, mov_ref, mov_user)
         VALUES ($1, $2, $3, NOW(), 'TagInfo-Transfer', $4, $5)`,
        [epc, place_id, zone_id, mov_ref, mov_user]
      );
    }

    res.json({ success: true, transferred_count: epcs.length });
  } catch (error) {
    console.error('Error transferring tags:', error);
    res.status(500).json({ error: 'Failed to transfer tags', details: error.message });
  }
};

// Ottieni movimenti per range di date
exports.getMovementsByDateRange = async (req, res) => {
  const { startDate, endDate } = req.query;

  if (!startDate || !endDate) {
    return res.status(400).json({ error: 'startDate and endDate are required' });
  }

  try {
    const movements = await Movement.findByDateRange(startDate, endDate);
    res.json(movements);
  } catch (error) {
    console.error('Error fetching movements by date range:', error);
    res.status(500).json({ error: 'Failed to fetch movements' });
  }
};
