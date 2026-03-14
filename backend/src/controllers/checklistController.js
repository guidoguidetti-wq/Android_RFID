const Checklist = require('../models/Checklist');
const pool = require('../db/config');

/**
 * GET /api/checklist
 * Ottieni checklist con filtro opzionale per tipo
 */
exports.getAll = async (req, res) => {
  try {
    const { type } = req.query;

    let checklists;
    if (type) {
      console.log(`Fetching checklists of type: ${type}`);
      checklists = await Checklist.findByType(type);
    } else {
      checklists = await Checklist.getAll();
    }

    res.json(checklists);
  } catch (error) {
    console.error('Error fetching checklists:', error);
    res.status(500).json({
      error: 'Failed to fetch checklists',
      details: error.message
    });
  }
};

/**
 * GET /api/checklist/:id
 * Ottieni una checklist specifica
 */
exports.getById = async (req, res) => {
  try {
    const { id } = req.params;

    const checklist = await Checklist.findById(id);

    if (!checklist) {
      return res.status(404).json({
        error: 'Checklist not found'
      });
    }

    res.json(checklist);
  } catch (error) {
    console.error('Error fetching checklist:', error);
    res.status(500).json({
      error: 'Failed to fetch checklist',
      details: error.message
    });
  }
};

/**
 * POST /api/checklist/create-from-scan
 * Crea una checklist a partire da tag RFID scansionati
 */
exports.createFromScan = async (req, res) => {
  const { chk_code, chk_notes, items } = req.body;

  if (!chk_code || !Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: 'chk_code and items array are required' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Recupera place di default dal primo utente
    const userResult = await client.query('SELECT usr_def_place FROM users LIMIT 1');
    const chk_place = userResult.rows[0]?.usr_def_place || null;

    // Inserisce la checklist
    const chkResult = await client.query(
      `INSERT INTO checklist (chk_code, chk_type, chk_place, chk_notes, chk_creationdate)
       VALUES ($1, 'EPC', $2, $3, CURRENT_DATE) RETURNING chk_id`,
      [chk_code, chk_place, chk_notes || null]
    );
    const chk_id = chkResult.rows[0].chk_id;

    // Inserisce un record per ogni EPC in checklist_products_items
    for (const item of items) {
      await client.query(
        `INSERT INTO checklist_products_items (cki_chk_id, cki_product_id, cki_epc)
         VALUES ($1, $2, $3)`,
        [chk_id, item.product_id || null, item.epc]
      );
    }

    // Raggruppa per product_id e inserisce in checklist_products
    const productGroups = {};
    for (const item of items) {
      const pid = item.product_id || null;
      const key = pid || '__NONE__';
      productGroups[key] = { pid, count: (productGroups[key]?.count || 0) + 1 };
    }

    for (const { pid, count } of Object.values(productGroups)) {
      await client.query(
        `INSERT INTO checklist_products (ckp_chk_id, ckp_product_id, ckp_qta)
         VALUES ($1, $2, $3)`,
        [chk_id, pid, count]
      );
    }

    await client.query('COMMIT');

    res.json({
      success: true,
      chk_id,
      items_count: items.length,
      products_count: Object.keys(productGroups).length
    });
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Error creating checklist from scan:', error);
    res.status(500).json({ error: 'Failed to create checklist', details: error.message });
  } finally {
    client.release();
  }
};

/**
 * POST /api/checklist
 * Crea una nuova checklist
 */
exports.create = async (req, res) => {
  try {
    const { code, type, place, zone, notes } = req.body;

    if (!code || !type) {
      return res.status(400).json({
        error: 'code and type are required'
      });
    }

    const checklist = await Checklist.create({ code, type, place, zone, notes });

    res.status(201).json({
      success: true,
      checklist
    });
  } catch (error) {
    console.error('Error creating checklist:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to create checklist',
      details: error.message
    });
  }
};
