const Inventory = require('../models/Inventory');
const InventoryItem = require('../models/InventoryItem');
const Item = require('../models/Item');
const pool = require('../db/config');

/**
 * GET /api/inventories/open/:placeId
 * Ottieni tutti gli inventari aperti per un Place
 */
exports.getOpenByPlace = async (req, res) => {
  try {
    const { placeId } = req.params;

    console.log(`Fetching open inventories for place: ${placeId}`);

    const inventories = await Inventory.findOpenByPlace(placeId);

    res.json(inventories);
  } catch (error) {
    console.error('Error fetching open inventories:', error);
    res.status(500).json({
      error: 'Failed to fetch inventories',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId
 * Ottieni dettagli di un inventario specifico
 */
exports.getById = async (req, res) => {
  try {
    const { invId } = req.params;

    const inventory = await Inventory.findById(invId);

    if (!inventory) {
      return res.status(404).json({
        error: 'Inventory not found'
      });
    }

    res.json(inventory);
  } catch (error) {
    console.error('Error fetching inventory:', error);
    res.status(500).json({
      error: 'Failed to fetch inventory',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId/items
 * Ottieni tutti gli items di un inventario
 */
exports.getItems = async (req, res) => {
  try {
    const { invId } = req.params;

    const items = await InventoryItem.getItemsByInventory(invId);

    res.json(items);
  } catch (error) {
    console.error('Error fetching inventory items:', error);
    res.status(500).json({
      error: 'Failed to fetch items',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId/count
 * Ottieni il conteggio items di un inventario
 */
exports.getItemsCount = async (req, res) => {
  try {
    const { invId } = req.params;

    const count = await InventoryItem.getCountByInventory(invId);

    res.json({ count });
  } catch (error) {
    console.error('Error counting inventory items:', error);
    res.status(500).json({
      error: 'Failed to count items',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId/items-details
 * Ottieni items con dettagli prodotto (3-table JOIN)
 */
exports.getItemsWithDetails = async (req, res) => {
  try {
    const { invId } = req.params;

    const result = await pool.query(
      `SELECT
         ii.int_epc as epc,
         i.item_product_id as product_id,
         p.fld01,
         p.fld02,
         p.fld03,
         p.fldd01
       FROM "inventory_items" ii
       LEFT JOIN "Items" i ON ii.int_epc = i.item_id
       LEFT JOIN "Products" p ON i.item_product_id = p.product_id
       WHERE ii.int_inv_id = $1
       ORDER BY ii.int_epc`,
      [invId]
    );

    res.json(result.rows);
  } catch (error) {
    console.error('Error fetching inventory items with details:', error);
    res.status(500).json({
      error: 'Failed to fetch items details',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId/stats
 * Ottieni statistiche di un inventario
 */
exports.getStats = async (req, res) => {
  try {
    const { invId } = req.params;

    const stats = await InventoryItem.getInventoryStats(invId);

    res.json(stats);
  } catch (error) {
    console.error('Error fetching inventory stats:', error);
    res.status(500).json({
      error: 'Failed to fetch stats',
      details: error.message
    });
  }
};

/**
 * POST /api/inventories
 * Crea un nuovo inventario
 */
exports.create = async (req, res) => {
  try {
    const { invId, name, note, placeId, userId } = req.body;

    // Validazione input
    if (!invId || !name || !placeId || !userId) {
      return res.status(400).json({
        error: 'invId, name, placeId, and userId are required'
      });
    }

    console.log(`Creating inventory: ${invId} for place ${placeId} by user ${userId}`);

    // Verifica che l'inventario non esista già
    const existing = await Inventory.findById(invId);
    if (existing) {
      return res.status(409).json({
        error: 'Inventory ID already exists'
      });
    }

    const inventory = await Inventory.create({ invId, name, note, placeId, userId });

    res.status(201).json({
      success: true,
      inventory
    });
  } catch (error) {
    console.error('Error creating inventory:', error);
    res.status(500).json({
      error: 'Failed to create inventory',
      details: error.message
    });
  }
};

/**
 * PUT /api/inventories/:invId/state
 * Aggiorna lo stato di un inventario (open/closed)
 */
exports.updateState = async (req, res) => {
  try {
    const { invId } = req.params;
    const { state } = req.body;

    // Validazione stato
    if (!state || !['open', 'closed'].includes(state)) {
      return res.status(400).json({
        error: 'Valid state required (open or closed)'
      });
    }

    console.log(`Updating inventory ${invId} state to: ${state}`);

    const inventory = await Inventory.updateState(invId, state);

    if (!inventory) {
      return res.status(404).json({
        error: 'Inventory not found'
      });
    }

    res.json({
      success: true,
      inventory
    });
  } catch (error) {
    console.error('Error updating inventory state:', error);
    res.status(500).json({
      error: 'Failed to update inventory state',
      details: error.message
    });
  }
};

/**
 * PUT /api/inventories/:invId
 * Aggiorna dati di un inventario
 */
exports.update = async (req, res) => {
  try {
    const { invId } = req.params;
    const updates = req.body;

    const inventory = await Inventory.update(invId, updates);

    if (!inventory) {
      return res.status(404).json({
        error: 'Inventory not found'
      });
    }

    res.json({
      success: true,
      inventory
    });
  } catch (error) {
    console.error('Error updating inventory:', error);
    res.status(500).json({
      error: 'Failed to update inventory',
      details: error.message
    });
  }
};

/**
 * POST /api/inventories/:invId/scan
 * Aggiungi un tag scannerizzato a un inventario
 */
exports.addScan = async (req, res) => {
  try {
    const { invId } = req.params;
    const { epc, mode, placeId, zoneId } = req.body;

    // Validazione
    if (!epc) {
      return res.status(400).json({
        error: 'EPC required'
      });
    }

    console.log(`Adding scan to inventory ${invId}: EPC ${epc}, Mode: ${mode || 'mode_c'}`);

    let shouldAddToInventory = true;
    let item = null;

    // Mode-based processing
    if (mode === 'mode_a') {
      // Solo EPC censiti - verificare esistenza
      const exists = await Item.existsByEpc(epc);
      if (!exists) {
        console.log(`EPC ${epc} non censito - skipped (mode_a)`);
        shouldAddToInventory = false;
      }
    } else if (mode === 'mode_b') {
      // Tutti i tags con registrazione dei non censiti
      const exists = await Item.existsByEpc(epc);
      if (!exists) {
        console.log(`EPC ${epc} non censito - creating new item (mode_b)`);
        await Item.createFromScan(epc, placeId, zoneId);
      }
    }
    // mode_c: nessun controllo, comportamento attuale

    // Aggiungi a inventory_items solo se necessario
    if (shouldAddToInventory) {
      item = await InventoryItem.addItem(invId, epc);
    }

    // Ottieni conteggio totale aggiornato
    const count = await InventoryItem.getCountByInventory(invId);

    res.json({
      success: true,
      item,
      totalCount: count,
      isNew: !!item
    });
  } catch (error) {
    console.error('Error adding scan:', error);
    res.status(500).json({
      error: 'Failed to add scan',
      details: error.message
    });
  }
};

/**
 * DELETE /api/inventories/:invId
 * Elimina un inventario
 */
exports.deleteInventory = async (req, res) => {
  try {
    const { invId } = req.params;

    const deleted = await Inventory.delete(invId);

    if (!deleted) {
      return res.status(404).json({
        error: 'Inventory not found'
      });
    }

    res.json({
      success: true,
      message: `Inventory '${invId}' deleted`
    });
  } catch (error) {
    console.error('Error deleting inventory:', error);
    res.status(500).json({
      error: 'Failed to delete inventory',
      details: error.message
    });
  }
};

/**
 * DELETE /api/inventories/:invId/items/:epc
 * Rimuovi un item da un inventario
 */
exports.removeItem = async (req, res) => {
  try {
    const { invId, epc } = req.params;

    const removed = await InventoryItem.removeItem(invId, epc);

    if (!removed) {
      return res.status(404).json({
        error: 'Item not found in inventory'
      });
    }

    // Ottieni conteggio aggiornato
    const count = await InventoryItem.getCountByInventory(invId);

    res.json({
      success: true,
      message: `Item '${epc}' removed`,
      totalCount: count
    });
  } catch (error) {
    console.error('Error removing item:', error);
    res.status(500).json({
      error: 'Failed to remove item',
      details: error.message
    });
  }
};

/**
 * DELETE /api/inventories/:invId/items
 * Svuota un inventario (rimuovi tutti gli items)
 */
exports.clearItems = async (req, res) => {
  try {
    const { invId } = req.params;

    const removed = await InventoryItem.clearInventory(invId);

    res.json({
      success: true,
      message: `Inventory cleared`,
      itemsRemoved: removed
    });
  } catch (error) {
    console.error('Error clearing inventory:', error);
    res.status(500).json({
      error: 'Failed to clear inventory',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories
 * Ottieni tutti gli inventari con filtri opzionali
 */
exports.getAll = async (req, res) => {
  try {
    const { state, placeId, limit, offset } = req.query;

    const filters = {};
    if (state) filters.state = state;
    if (placeId) filters.placeId = placeId;
    if (limit) filters.limit = parseInt(limit);
    if (offset) filters.offset = parseInt(offset);

    const inventories = await Inventory.getAll(filters);

    res.json(inventories);
  } catch (error) {
    console.error('Error fetching inventories:', error);
    res.status(500).json({
      error: 'Failed to fetch inventories',
      details: error.message
    });
  }
};
