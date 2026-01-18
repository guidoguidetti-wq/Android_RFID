const Inventory = require('../models/Inventory');
const InventoryItem = require('../models/InventoryItem');
const Item = require('../models/Item');
const ChecklistProduct = require('../models/ChecklistProduct');
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
 * Include status expected/unexpected per filtri e colorazione
 */
exports.getItemsWithDetails = async (req, res) => {
  try {
    const { invId } = req.params;

    const result = await pool.query(
      `SELECT
         ii.int_epc as epc,
         ii.inv_expected,
         ii.inv_unexpected,
         ii.inv_lost,
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
 * Crea un nuovo inventario (inv_id auto-generato dal database)
 */
exports.create = async (req, res) => {
  try {
    const {
      name,
      note,
      placeId,
      invChkId,      // ID checklist (0 se non da checklist)
      invZones,      // Zone separate da virgola (es: "Z1,Z2,Z3") - DEPRECATED
      invDetPlace,   // Place per detected items
      invDetZone,    // Zone per detected items
      invMisPlace,   // Place per missed items
      invMisZone,    // Zone per missed items
      invLast,       // Boolean: true se inventario da giacenza RFID
      invLastPlace,  // Place per inventario da giacenza
      invLastZones   // Zone per inventario da giacenza (separate da virgola)
    } = req.body;

    // Validazione input (invId e userId non richiesti)
    if (!name || !placeId) {
      return res.status(400).json({
        error: 'name and placeId are required'
      });
    }

    console.log(`Creating inventory '${name}' for place ${placeId}`);
    console.log(`  chkId: ${invChkId}, invLast: ${invLast}`);
    console.log(`  lastPlace: ${invLastPlace}, lastZones: ${invLastZones}`);
    console.log(`  detected: ${invDetPlace}/${invDetZone}, missed: ${invMisPlace}/${invMisZone}`);

    const inventory = await Inventory.create({
      name,
      note,
      placeId,
      chkId: invChkId || 0,
      zones: invZones || null,
      detPlace: invDetPlace || null,
      detZone: invDetZone || null,
      misPlace: invMisPlace || null,
      misZone: invMisZone || null,
      invLast: invLast || false,
      invLastPlace: invLastPlace || null,
      invLastZones: invLastZones || null
    });

    res.status(201).json({
      success: true,
      inventory
    });
  } catch (error) {
    console.error('Error creating inventory:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to create inventory',
      details: error.message
    });
  }
};

/**
 * PUT /api/inventories/:invId/state
 * Aggiorna lo stato di un inventario (OPEN/CLOSE)
 */
exports.updateState = async (req, res) => {
  try {
    const { invId } = req.params;
    const { state } = req.body;

    // Validazione stato
    if (!state || !['OPEN', 'CLOSE', 'open', 'closed'].includes(state)) {
      return res.status(400).json({
        error: 'Valid state required (OPEN or CLOSE)'
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
 * PUT /api/inventories/:invId/close
 * Chiudi un inventario (imposta stato a CLOSE)
 */
exports.closeInventory = async (req, res) => {
  try {
    const { invId } = req.params;

    console.log(`Closing inventory ${invId}`);

    const inventory = await Inventory.updateState(invId, 'CLOSE');

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
    console.error('Error closing inventory:', error);
    res.status(500).json({
      error: 'Failed to close inventory',
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
 * Restituisce anche productId per la classificazione Expected/Unexpected
 * Calcola automaticamente lo status (expected/unexpected) in base alla modalità inventario
 */
exports.addScan = async (req, res) => {
  try {
    const { invId } = req.params;
    const { epc, mode, placeId, zoneId, productFilters, status: clientStatus } = req.body;

    // Validazione
    if (!epc) {
      return res.status(400).json({
        error: 'EPC required'
      });
    }

    console.log(`Adding scan to inventory ${invId}: EPC ${epc}, Mode: ${mode || 'mode_c'}`);

    let shouldAddToInventory = true;
    let item = null;
    let productId = null;
    let calculatedStatus = clientStatus || null;

    // Mode-based processing
    if (mode === 'mode_a') {
      // Solo EPC censiti - verificare esistenza
      const exists = await Item.existsByEpc(epc);
      if (!exists) {
        console.log(`EPC ${epc} non censito - skipped (mode_a)`);
        shouldAddToInventory = false;
      } else if (productFilters && Object.keys(productFilters).length > 0) {
        // NUOVO: Applicare filtri prodotto se mode_a è attivo
        const productMatches = await Item.matchesProductFilters(epc, productFilters);
        if (!productMatches) {
          console.log(`EPC ${epc} filtered by product filters:`, productFilters);
          shouldAddToInventory = false;
        }
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

    // Recupera productId dall'item (per classificazione Expected/Unexpected)
    const itemData = await Item.findByEpc(epc);
    if (itemData) {
      productId = itemData.item_product_id || null;
    }

    // Se status non fornito dal client, calcolalo in base alla modalità inventario
    if (!calculatedStatus && shouldAddToInventory) {
      const inventory = await Inventory.findById(invId);

      if (inventory) {
        // Modalità Checklist
        if (inventory.inv_chk_id && inventory.inv_chk_id !== 0) {
          if (productId) {
            // Verifica se il prodotto è nella checklist
            const checklistProduct = await ChecklistProduct.findProductInChecklist(inventory.inv_chk_id, productId);
            if (checklistProduct && checklistProduct.ckp_qta > 0) {
              // Conta quanti EPC di questo prodotto sono già nell'inventario
              const existingCount = await pool.query(
                `SELECT COUNT(*) as cnt FROM "inventory_items" ii
                 JOIN "Items" i ON ii.int_epc = i.item_id
                 WHERE ii.int_inv_id = $1 AND i.item_product_id = $2`,
                [invId, productId]
              );
              const currentCount = parseInt(existingCount.rows[0].cnt) || 0;

              if (currentCount < checklistProduct.ckp_qta) {
                calculatedStatus = 'expected';
              } else {
                calculatedStatus = 'unexpected';
              }
            } else {
              calculatedStatus = 'unexpected';
            }
          } else {
            calculatedStatus = 'unexpected';
          }
        }
        // Modalità Last-Place
        else if (inventory.inv_last === true) {
          const lastPlace = inventory.inv_last_place;
          // Zone possono essere separate da ; o ,
          const lastZones = inventory.inv_last_zones
            ? inventory.inv_last_zones.split(/[;,]/).map(z => z.trim()).filter(z => z.length > 0)
            : [];

          // Verifica se l'EPC era atteso in base a place_last e zone_last
          let query = `SELECT 1 FROM "Items" WHERE item_id = $1 AND place_last = $2`;
          const params = [epc, lastPlace];

          if (lastZones.length > 0) {
            query += ` AND zone_last = ANY($3)`;
            params.push(lastZones);
          }

          const expectedResult = await pool.query(query, params);
          calculatedStatus = expectedResult.rows.length > 0 ? 'expected' : 'unexpected';
        }
        // Modalità normale: nessuno status
      }
    }

    console.log(`Status for EPC ${epc}: ${calculatedStatus || 'none'}`);

    // Aggiungi a inventory_items solo se necessario (con status)
    if (shouldAddToInventory) {
      item = await InventoryItem.addItem(invId, epc, calculatedStatus);
    }

    // Ottieni conteggio totale aggiornato
    const count = await InventoryItem.getCountByInventory(invId);

    // Calcola contatori Expected/Unexpected/Lost aggiornati
    const countersResult = await pool.query(
      `SELECT
        COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
        COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
        COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items"
       WHERE int_inv_id = $1`,
      [invId]
    );

    const counters = {
      expectedCount: parseInt(countersResult.rows[0].expected_count) || 0,
      unexpectedCount: parseInt(countersResult.rows[0].unexpected_count) || 0,
      lostCount: parseInt(countersResult.rows[0].lost_count) || 0
    };

    // Per checklist mode: calcola lost come totalExpected - expectedCount
    const inventory = await Inventory.findById(invId);
    if (inventory && inventory.inv_chk_id && inventory.inv_chk_id !== 0) {
      // Ottieni totalExpected dalla checklist
      const checklistProducts = await ChecklistProduct.getByChecklistId(inventory.inv_chk_id);
      const totalExpected = checklistProducts.reduce((sum, p) => sum + (parseInt(p.ckp_qta) || 0), 0);
      counters.lostCount = Math.max(0, totalExpected - counters.expectedCount);
    }

    res.json({
      success: true,
      item,
      totalCount: count,
      isNew: !!item,
      epc: epc,
      productId: productId,
      status: calculatedStatus,
      counters: counters  // NUOVO: contatori aggiornati
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

/**
 * GET /api/inventories/:invId/expectations
 * Ottieni le aspettative per un inventario (per logica Expected/Unexpected/Lost)
 * Restituisce diverse info in base alla modalità dell'inventario:
 * - checklist: prodotti della checklist con quantità
 * - last_place: lista di EPCs attesi basata su place_last/zone_last
 * - normal: nessuna aspettativa
 */
exports.getExpectations = async (req, res) => {
  try {
    const { invId } = req.params;

    console.log(`Fetching expectations for inventory ${invId}`);

    // Recupera dettagli inventario
    const inventory = await Inventory.findById(invId);
    if (!inventory) {
      return res.status(404).json({ error: 'Inventory not found' });
    }

    const result = {
      mode: 'normal',
      totalExpected: 0,
      expectedItems: [],        // Per last-place mode: lista di EPCs
      checklistProducts: []     // Per checklist mode: product_id -> qty
    };

    // Mode 1: Checklist-based (inv_chk_id != 0)
    if (inventory.inv_chk_id && inventory.inv_chk_id !== 0) {
      result.mode = 'checklist';

      const products = await ChecklistProduct.getByChecklistId(inventory.inv_chk_id);
      result.checklistProducts = products;
      result.totalExpected = products.reduce((sum, p) => sum + (parseInt(p.ckp_qta) || 0), 0);

      console.log(`Checklist mode: ${products.length} products, total expected: ${result.totalExpected}`);
    }
    // Mode 2: Last-place based (inv_last = true)
    else if (inventory.inv_last === true) {
      result.mode = 'last_place';

      const lastPlace = inventory.inv_last_place;
      // Zone possono essere separate da ; o ,
      const lastZones = inventory.inv_last_zones
        ? inventory.inv_last_zones.split(/[;,]/).map(z => z.trim()).filter(z => z.length > 0)
        : [];

      console.log(`Last-place mode: place=${lastPlace}, zones=${lastZones.join(',')}, raw zones='${inventory.inv_last_zones}'`);

      // Query per ottenere EPCs attesi
      let query = `SELECT item_id FROM "Items" WHERE place_last = $1`;
      const params = [lastPlace];

      if (lastZones.length > 0) {
        query += ` AND zone_last = ANY($2)`;
        params.push(lastZones);
      }

      console.log(`Executing query: ${query} with params:`, params);

      const itemsResult = await pool.query(query, params);
      result.expectedItems = itemsResult.rows.map(r => r.item_id);
      result.totalExpected = result.expectedItems.length;

      console.log(`Found ${result.totalExpected} expected items in place ${lastPlace} with zones [${lastZones.join(',')}]`);

      // ✅ Pre-popola inventory_items con gli EPCs attesi come "lost"
      // Questo permette di vedere i lost nella pagina Details
      if (result.expectedItems.length > 0) {
        console.log(`Pre-populating lost items for Stock inventory ${invId}...`);

        // Prima verifica quanti items esistono già
        const existingResult = await pool.query(
          `SELECT int_epc FROM "inventory_items" WHERE int_inv_id = $1`,
          [invId]
        );
        const existingEpcs = new Set(existingResult.rows.map(r => r.int_epc));
        console.log(`Found ${existingEpcs.size} existing items in inventory`);

        // Inserisci solo gli EPC attesi che non esistono già
        let insertedCount = 0;
        for (const epc of result.expectedItems) {
          if (!existingEpcs.has(epc)) {
            try {
              await pool.query(
                `INSERT INTO "inventory_items" (int_inv_id, int_epc, inv_lost, inv_expected, inv_unexpected)
                 VALUES ($1, $2, true, false, false)`,
                [invId, epc]
              );
              insertedCount++;
            } catch (e) {
              console.warn(`Could not insert lost item ${epc}:`, e.message);
            }
          }
        }

        console.log(`Pre-populated ${insertedCount} new lost items`);
      }
    }
    // Mode 3: Normal - no expectations
    else {
      console.log('Normal mode: no expectations');
    }

    res.json(result);
  } catch (error) {
    console.error('Error getting expectations:', error);
    res.status(500).json({
      error: 'Failed to get expectations',
      details: error.message
    });
  }
};
