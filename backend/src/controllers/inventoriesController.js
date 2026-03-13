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
 * GET /api/inventories/:invId/counters
 * Ottieni solo i contatori (expected/unexpected/lost) di un inventario
 * Endpoint ottimizzato per loading rapido - nessun JOIN, solo COUNT aggregati
 */
exports.getCounters = async (req, res) => {
  try {
    const { invId } = req.params;

    console.log(`Fetching counters for inventory ${invId}`);

    const result = await pool.query(
      `SELECT
        COUNT(*) as total_count,
        COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
        COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
        COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items"
       WHERE int_inv_id = $1`,
      [invId]
    );

    const counters = {
      total_count: parseInt(result.rows[0].total_count) || 0,
      expected_count: parseInt(result.rows[0].expected_count) || 0,
      unexpected_count: parseInt(result.rows[0].unexpected_count) || 0,
      lost_count: parseInt(result.rows[0].lost_count) || 0
    };

    res.json(counters);
  } catch (error) {
    console.error('Error fetching inventory counters:', error);
    res.status(500).json({
      error: 'Failed to fetch counters',
      details: error.message
    });
  }
};

/**
 * GET /api/inventories/:invId/epcs
 * Ottieni solo la lista di EPCs di un inventario (senza dettagli prodotto)
 * Endpoint ottimizzato per loading rapido - nessun JOIN con Items o Products
 */
exports.getEpcs = async (req, res) => {
  try {
    const { invId } = req.params;

    console.log(`Fetching EPCs for inventory ${invId}`);

    const result = await pool.query(
      `SELECT int_epc, inv_lost
       FROM "inventory_items"
       WHERE int_inv_id = $1
       ORDER BY int_epc`,
      [invId]
    );

    res.json(result.rows);
  } catch (error) {
    console.error('Error fetching inventory EPCs:', error);
    res.status(500).json({
      error: 'Failed to fetch EPCs',
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
    let inventory = null;  // ✅ Dichiarata fuori per riuso successivo

    // Carica l'inventario per determinare la modalità
    inventory = await Inventory.findById(invId);
    if (!inventory) {
      return res.status(404).json({ error: 'Inventory not found' });
    }

    // ========== NUOVA LOGICA: Distingue le 3 modalità ==========

    // Determina modalità inventario da inv_type (fallback su vecchia logica se null)
    // 1=Stock, 2=Checklist(SKU), 3=Checklist(EPC) [non gestito], 4=Normal
    const invType = inventory.inv_type;
    const isChecklistMode = invType ? invType === 2 : !!(inventory.inv_chk_id && inventory.inv_chk_id !== 0);
    const isStockMode    = invType ? invType === 1 : inventory.inv_last === true;
    const isNormalMode   = !isChecklistMode && !isStockMode;

    console.log(`Inventory mode: ${isChecklistMode ? 'CHECKLIST' : isStockMode ? 'STOCK' : 'NORMAL'}`);

    // ========== MODALITÀ NORMAL ==========
    if (isNormalMode) {
      // Counter principale: tutti i tag scannerizzati (scrittura in inventory_items)
      // Counter "Validated": solo tag con inv_expected = true

      if (mode === 'mode_b') {
        // Tutti - Incrementa principale E Validated
        console.log(`Normal mode (mode_b): Adding EPC ${epc} as validated`);

        // Se EPC non esiste in Items, crealo
        const exists = await Item.existsByEpc(epc);
        if (!exists) {
          console.log(`EPC ${epc} non censito - creating new item (mode_b)`);
          await Item.createFromScan(epc, placeId, zoneId);
        }

        // Recupera productId
        const itemData = await Item.findByEpc(epc);
        if (itemData) {
          productId = itemData.item_product_id || null;
        }

        shouldAddToInventory = true;
        calculatedStatus = 'expected';  // inv_expected = true
      } else if (mode === 'mode_a') {
        // ✅ Solo Censiti - Incrementa principale SEMPRE, Validated solo se in Items
        // NUOVO COMPORTAMENTO: Salva SEMPRE in inventory_items (anche se non in Items)
        const exists = await Item.existsByEpc(epc);

        if (exists) {
          console.log(`Normal mode (mode_a): EPC ${epc} in Items - adding as validated`);

          // Recupera productId
          const itemData = await Item.findByEpc(epc);
          if (itemData) {
            productId = itemData.item_product_id || null;
          }

          // Applica filtri prodotto se presenti
          if (productFilters && Object.keys(productFilters).length > 0) {
            const productMatches = await Item.matchesProductFilters(epc, productFilters);
            if (!productMatches) {
              console.log(`EPC ${epc} filtered by product filters:`, productFilters);
              shouldAddToInventory = false;
            }
          }

          if (shouldAddToInventory) {
            calculatedStatus = 'expected';  // inv_expected = true
          }
        } else {
          // ✅ NUOVO: EPC non censito → SALVA comunque in inventory_items con inv_expected = false
          console.log(`Normal mode (mode_a): EPC ${epc} NOT in Items - adding with inv_expected=false (no validation)`);
          shouldAddToInventory = true;  // ✅ Salva in inventory_items
          calculatedStatus = null;  // ✅ inv_expected = false, inv_unexpected = false (tag non validato)
          productId = null;  // Non ha product_id
        }
      } else {
        // mode_c: comportamento legacy (tutti expected)
        const itemData = await Item.findByEpc(epc);
        if (itemData) {
          productId = itemData.item_product_id || null;
        }
        shouldAddToInventory = true;
        calculatedStatus = 'expected';
      }
    }
    // ========== MODALITÀ CHECKLIST ==========
    else if (isChecklistMode) {
      console.log(`Checklist mode: Processing EPC ${epc}`);

      // 1. Verifica se EPC è censito in Items - se non trovato, ignora
      const itemData = await Item.findByEpc(epc);
      if (!itemData) {
        console.log(`Checklist: EPC ${epc} NOT in Items - IGNORED`);
        shouldAddToInventory = false;
      } else {
        productId = itemData.item_product_id || null;
        shouldAddToInventory = true;

        // 2. Verifica se product in checklist_products
        if (productId) {
          const checklistProduct = await ChecklistProduct.findProductInChecklist(inventory.inv_chk_id, productId);

          if (checklistProduct) {
            const currentQtaExp = parseInt(checklistProduct.ckp_qta_exp) || 0;
            const expectedQta = parseInt(checklistProduct.ckp_qta) || 0;

            if (currentQtaExp + 1 <= expectedQta) {
              console.log(`Checklist: Product ${productId} matched (${currentQtaExp + 1}/${expectedQta}) - EXPECTED`);
              await ChecklistProduct.incrementExpected(inventory.inv_chk_id, productId);
              calculatedStatus = 'expected';
            } else {
              console.log(`Checklist: Product ${productId} exceeded (${currentQtaExp + 1}/${expectedQta}) - UNEXPECTED`);
              await ChecklistProduct.incrementUnexpected(inventory.inv_chk_id, productId);
              calculatedStatus = 'unexpected';
            }
          } else {
            console.log(`Checklist: Product ${productId} NOT in checklist - UNEXPECTED`);
            calculatedStatus = 'unexpected';
          }
        } else {
          // In Items ma senza productId associato
          console.log(`Checklist: EPC ${epc} has no productId - UNEXPECTED`);
          calculatedStatus = 'unexpected';
        }
      }
    }
    // ========== MODALITÀ STOCK (Last-Place) ==========
    else if (isStockMode) {
      // Logica esistente per Stock mode
      const exists = await Item.existsByEpc(epc);
      if (!exists && mode === 'mode_b') {
        console.log(`EPC ${epc} non censito - creating new item (mode_b)`);
        await Item.createFromScan(epc, placeId, zoneId);
      } else if (!exists && mode === 'mode_a') {
        console.log(`Stock mode (mode_a): EPC ${epc} NOT in Items - skipping`);
        shouldAddToInventory = false;
      }

      if (shouldAddToInventory) {
        const itemData = await Item.findByEpc(epc);
        if (itemData) {
          productId = itemData.item_product_id || null;
        }

        const lastPlace = inventory.inv_last_place;
        const lastZones = inventory.inv_last_zones
          ? inventory.inv_last_zones.split(/[;,]/).map(z => z.trim()).filter(z => z.length > 0)
          : [];

        let query = `SELECT 1 FROM "Items" WHERE item_id = $1 AND place_last = $2`;
        const params = [epc, lastPlace];

        if (lastZones.length > 0) {
          query += ` AND zone_last = ANY($3)`;
          params.push(lastZones);
        }

        const expectedResult = await pool.query(query, params);
        calculatedStatus = expectedResult.rows.length > 0 ? 'expected' : 'unexpected';
      }
    }

    console.log(`Status for EPC ${epc}: ${calculatedStatus || 'none'}`);

    // Aggiungi a inventory_items solo se necessario (con status)
    if (shouldAddToInventory) {
      item = await InventoryItem.addItem(invId, epc, calculatedStatus);
    }

    // ✅ OTTIMIZZAZIONE: Combina count totale + counters in una sola query
    const countersResult = await pool.query(
      `SELECT
        COUNT(*) as total_count,
        COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
        COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
        COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items"
       WHERE int_inv_id = $1`,
      [invId]
    );

    const count         = parseInt(countersResult.rows[0].total_count)       || 0;
    const expectedCount = parseInt(countersResult.rows[0].expected_count)   || 0;
    const unexpectedCount = parseInt(countersResult.rows[0].unexpected_count) || 0;
    const lostCount     = parseInt(countersResult.rows[0].lost_count)       || 0;

    const counters = {
      expectedCount,
      unexpectedCount,
      lostCount,
      ignoredCount: Math.max(0, count - expectedCount - unexpectedCount - lostCount)
    };

    res.json({
      success: true,
      item,
      totalCount: count,
      isNew: !!item,
      epc: epc,
      productId: productId,
      status: calculatedStatus,
      counters: counters
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
 * POST /api/inventories/:invId/batch-scan
 * Processa un array di EPCs in un'unica chiamata
 * Ottimizzazione: query DB ridotte da N×5 a ~4 totali indipendentemente dalla dimensione del batch
 */
exports.addBatchScan = async (req, res) => {
  try {
    const { invId } = req.params;
    const { epcs, mode, placeId, zoneId, productFilters } = req.body;

    if (!epcs || !Array.isArray(epcs) || epcs.length === 0) {
      return res.status(400).json({ error: 'epcs array required and must not be empty' });
    }

    console.log(`Batch scan for inventory ${invId}: ${epcs.length} EPCs, mode: ${mode || 'mode_c'}`);

    // ── 1. Carica inventario (1 query) ──────────────────────────────────────
    const inventory = await Inventory.findById(invId);
    if (!inventory) {
      return res.status(404).json({ error: 'Inventory not found' });
    }

    // Determina modalità inventario da inv_type (fallback su vecchia logica se null)
    const invType = inventory.inv_type;
    const isChecklistMode = invType ? invType === 2 : !!(inventory.inv_chk_id && inventory.inv_chk_id !== 0);
    const isStockMode    = invType ? invType === 1 : inventory.inv_last === true;
    const isNormalMode   = !isChecklistMode && !isStockMode;

    // ── 2. Carica items già presenti in inventory_items per questo batch (1 query) ──
    const existingResult = await pool.query(
      `SELECT int_epc, inv_lost FROM "inventory_items" WHERE int_inv_id = $1 AND int_epc = ANY($2)`,
      [invId, epcs]
    );
    const existingMap = new Map(); // epc -> { inv_lost }
    existingResult.rows.forEach(r => existingMap.set(r.int_epc, { inv_lost: r.inv_lost }));

    // Tieni solo EPCs non ancora scansionati (o che erano "lost" → da aggiornare)
    const epcsToProcess = epcs.filter(epc => {
      const ex = existingMap.get(epc);
      return !ex || ex.inv_lost === true;
    });

    if (epcsToProcess.length === 0) {
      // Tutti già scansionati: restituisci solo i counter attuali
      const cRes = await pool.query(
        `SELECT COUNT(*) as total_count,
                COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
                COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
                COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
         FROM "inventory_items" WHERE int_inv_id = $1`,
        [invId]
      );
      return res.json({
        success: true,
        processedCount: 0,
        counters: {
          expectedCount: parseInt(cRes.rows[0].expected_count) || 0,
          unexpectedCount: parseInt(cRes.rows[0].unexpected_count) || 0,
          lostCount: parseInt(cRes.rows[0].lost_count) || 0
        }
      });
    }

    // ── 3. Carica dati Items per tutti gli EPCs in una query (1 query) ───────
    const itemsResult = await pool.query(
      `SELECT item_id, item_product_id, place_last, zone_last FROM "Items" WHERE item_id = ANY($1)`,
      [epcsToProcess]
    );
    const itemsMap = new Map(); // epc -> { item_product_id, place_last, zone_last }
    itemsResult.rows.forEach(r => itemsMap.set(r.item_id, r));

    // ── 4. Setup per modalità specifiche (1 query ciascuna se necessario) ────
    let checklistProductsMap = null; // Map: productId -> { ckp_qta, ckp_qta_exp }
    let expectedEpcsSet = null;      // Per Stock mode

    if (isChecklistMode) {
      const products = await ChecklistProduct.getByChecklistId(inventory.inv_chk_id);
      checklistProductsMap = new Map();
      products.forEach(p => checklistProductsMap.set(String(p.ckp_product_id), {
        ckp_qta: parseInt(p.ckp_qta) || 0,
        ckp_qta_exp: parseInt(p.ckp_qta_exp) || 0
      }));
    } else if (isStockMode) {
      const lastPlace = inventory.inv_last_place;
      const lastZones = inventory.inv_last_zones
        ? inventory.inv_last_zones.split(/[;,]/).map(z => z.trim()).filter(z => z.length > 0)
        : [];
      let q = `SELECT item_id FROM "Items" WHERE place_last = $1`;
      const p = [lastPlace];
      if (lastZones.length > 0) { q += ` AND zone_last = ANY($2)`; p.push(lastZones); }
      const expRes = await pool.query(q, p);
      expectedEpcsSet = new Set(expRes.rows.map(r => r.item_id));
    }

    // ── 5. Classifica ogni EPC in memoria ────────────────────────────────────
    const toInsertNew  = []; // { epc, invExpected, invUnexpected }
    const toUpdateLost = []; // { epc, invExpected, invUnexpected }  (erano inv_lost=true)
    const checklistIncrements = new Map(); // productId -> { expected, unexpected }

    for (const epc of epcsToProcess) {
      const itemData = itemsMap.get(epc);
      const productId = itemData?.item_product_id ? String(itemData.item_product_id) : null;
      const isLostUpdate = existingMap.has(epc) && existingMap.get(epc).inv_lost === true;

      let calculatedStatus = null;
      let shouldAdd = true;

      if (isNormalMode) {
        if (mode === 'mode_b') {
          if (!itemData) {
            // Crea item se non esiste (necessariamente sequenziale, raro)
            await Item.createFromScan(epc, placeId, zoneId);
          }
          calculatedStatus = 'expected';
        } else if (mode === 'mode_a') {
          if (itemData) {
            if (productFilters && Object.keys(productFilters).length > 0) {
              const matches = await Item.matchesProductFilters(epc, productFilters);
              if (!matches) shouldAdd = false;
            }
            if (shouldAdd) calculatedStatus = 'expected';
          } else {
            calculatedStatus = null; // Non censito → salva senza validazione
          }
        } else {
          // mode_c
          calculatedStatus = 'expected';
        }
      } else if (isChecklistMode) {
        if (!itemData) {
          // EPC non censito in Items → ignora, non scrivere in inventory_items
          shouldAdd = false;
        } else if (productId && checklistProductsMap && checklistProductsMap.has(productId)) {
          const prod = checklistProductsMap.get(productId);
          const inc = checklistIncrements.get(productId) || { expected: 0, unexpected: 0 };
          const currentExpTotal = prod.ckp_qta_exp + inc.expected;
          if (currentExpTotal + 1 <= prod.ckp_qta) {
            calculatedStatus = 'expected';
            inc.expected++;
          } else {
            calculatedStatus = 'unexpected';
            inc.unexpected++;
          }
          checklistIncrements.set(productId, inc);
        } else {
          // In Items ma prodotto non in checklist (o nessun productId)
          calculatedStatus = 'unexpected';
        }
      } else if (isStockMode) {
        if (!itemData && mode === 'mode_a') {
          shouldAdd = false;
        } else {
          calculatedStatus = (expectedEpcsSet && expectedEpcsSet.has(epc)) ? 'expected' : 'unexpected';
        }
      }

      if (!shouldAdd) continue;

      const invExpected   = calculatedStatus === 'expected';
      const invUnexpected = calculatedStatus === 'unexpected';

      if (isLostUpdate) {
        toUpdateLost.push({ epc, invExpected, invUnexpected });
      } else {
        toInsertNew.push({ epc, invExpected, invUnexpected });
      }
    }

    // ── 6. UPDATE items che erano lost (loop, di solito pochi) ───────────────
    for (const item of toUpdateLost) {
      await pool.query(
        `UPDATE "inventory_items"
         SET inv_lost = false, inv_expected = $3, inv_unexpected = $4
         WHERE int_inv_id = $1 AND int_epc = $2`,
        [invId, item.epc, item.invExpected, item.invUnexpected]
      );
    }

    // ── 7. Bulk INSERT nuovi items (1 query) ──────────────────────────────────
    if (toInsertNew.length > 0) {
      const valuePlaceholders = toInsertNew.map((_, i) => {
        const b = i * 4;
        return `($${b + 1}, $${b + 2}, $${b + 3}, $${b + 4}, false)`;
      }).join(', ');
      const flatParams = toInsertNew.flatMap(item => [
        invId, item.epc, item.invExpected, item.invUnexpected
      ]);
      await pool.query(
        `INSERT INTO "inventory_items" (int_inv_id, int_epc, inv_expected, inv_unexpected, inv_lost)
         VALUES ${valuePlaceholders}`,
        flatParams
      );
    }

    // ── 8. Aggiorna counter checklist in batch (1 query per prodotto unico) ──
    for (const [productId, inc] of checklistIncrements) {
      if (inc.expected > 0) {
        await pool.query(
          `UPDATE "checklist_products" SET ckp_qta_exp = ckp_qta_exp + $1
           WHERE ckp_chl_id = $2 AND ckp_product_id = $3`,
          [inc.expected, inventory.inv_chk_id, productId]
        );
      }
      if (inc.unexpected > 0) {
        await pool.query(
          `UPDATE "checklist_products" SET ckp_qta_unexp = ckp_qta_unexp + $1
           WHERE ckp_chl_id = $2 AND ckp_product_id = $3`,
          [inc.unexpected, inventory.inv_chk_id, productId]
        );
      }
    }

    // ── 9. Counter finali (1 query) ───────────────────────────────────────────
    const countersResult = await pool.query(
      `SELECT COUNT(*) as total_count,
              COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
              COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
              COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items" WHERE int_inv_id = $1`,
      [invId]
    );

    const batchExpected   = parseInt(countersResult.rows[0].expected_count)   || 0;
    const batchUnexpected = parseInt(countersResult.rows[0].unexpected_count) || 0;
    const batchLost       = parseInt(countersResult.rows[0].lost_count)       || 0;
    const batchTotal      = parseInt(countersResult.rows[0].total_count)      || 0;

    const counters = {
      expectedCount:   batchExpected,
      unexpectedCount: batchUnexpected,
      lostCount:       batchLost,
      ignoredCount:    Math.max(0, batchTotal - batchExpected - batchUnexpected - batchLost)
    };

    const processedCount = toInsertNew.length + toUpdateLost.length;
    console.log(`Batch scan complete: ${processedCount} items processed out of ${epcs.length} EPCs`);

    res.json({
      success: true,
      processedCount,
      counters
    });
  } catch (error) {
    console.error('Error in batch scan:', error);
    res.status(500).json({ error: 'Failed to process batch scan', details: error.message });
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

    const inventory = await Inventory.findById(invId);
    if (!inventory) {
      return res.status(404).json({ error: 'Inventory not found' });
    }

    const invType = inventory.inv_type;
    const isChecklistMode = invType ? invType === 2 : !!(inventory.inv_chk_id && inventory.inv_chk_id !== 0);
    const isStockMode    = invType ? invType === 1 : inventory.inv_last === true;

    // Resetta counter checklist prima di eliminare gli items
    if (isChecklistMode) {
      console.log(`Clearing checklist inventory ${invId} - resetting checklist counters`);
      await ChecklistProduct.resetCounters(inventory.inv_chk_id);
    }

    // Elimina TUTTI i record (compresi inv_lost=true)
    const removed = await InventoryItem.clearInventory(invId);
    console.log(`Inventory ${invId} cleared: ${removed} items removed`);

    // Ripopola con i lost in base alla modalità
    let repopulated = 0;

    if (isStockMode && inventory.inv_last_place) {
      // Stock mode: ripopola da Items filtrati per place/zone
      const lastPlace = inventory.inv_last_place;
      const lastZones = inventory.inv_last_zones
        ? inventory.inv_last_zones.split(/[;,]/).map(z => z.trim()).filter(z => z.length > 0)
        : [];

      let insertQuery = `
        INSERT INTO "inventory_items" (int_inv_id, int_epc, inv_lost, inv_expected, inv_unexpected)
        SELECT $1, item_id, true, false, false
        FROM "Items"
        WHERE place_last = $2`;
      const insertParams = [invId, lastPlace];

      if (lastZones.length > 0) {
        insertQuery += ` AND zone_last = ANY($3)`;
        insertParams.push(lastZones);
      }

      const insertResult = await pool.query(insertQuery, insertParams);
      repopulated = insertResult.rowCount;
      console.log(`Stock mode: re-populated ${repopulated} lost items`);

    } else if (isChecklistMode) {
      // Checklist mode: niente pre-popolamento, Lost = totalExpected - expected (calcolato dinamicamente)
      console.log(`Checklist mode: cleared, no pre-population needed`);
    }

    res.json({
      success: true,
      message: `Inventory cleared`,
      itemsRemoved: removed,
      itemsRepopulated: repopulated
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

      // ✅ NUOVA LOGICA: Inizializza i counter della checklist all'apertura
      // Solo se l'inventario è vuoto (prima apertura)
      const existingItemsCount = await pool.query(
        `SELECT COUNT(*) as cnt FROM "inventory_items" WHERE int_inv_id = $1`,
        [invId]
      );
      const hasItems = parseInt(existingItemsCount.rows[0].cnt) > 0;

      if (!hasItems) {
        console.log(`Checklist inventory ${invId} is empty - initializing checklist counters`);
        await ChecklistProduct.resetCounters(inventory.inv_chk_id);
      } else {
        console.log(`Checklist inventory ${invId} has ${existingItemsCount.rows[0].cnt} items - syncing checklist counters with real data`);
        await ChecklistProduct.syncCountersWithInventory(inventory.inv_chk_id, invId);
      }

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

        // Singola query INSERT ... SELECT: inserisce in bulk solo gli EPC
        // non ancora presenti in inventory_items per questo inventario
        let insertQuery = `
          INSERT INTO "inventory_items" (int_inv_id, int_epc, inv_lost, inv_expected, inv_unexpected)
          SELECT $1, item_id, true, false, false
          FROM "Items"
          WHERE place_last = $2
            AND item_id NOT IN (
              SELECT int_epc FROM "inventory_items" WHERE int_inv_id = $1
            )`;
        const insertParams = [invId, lastPlace];

        if (lastZones.length > 0) {
          insertQuery += ` AND zone_last = ANY($3)`;
          insertParams.push(lastZones);
        }

        const insertResult = await pool.query(insertQuery, insertParams);
        console.log(`Pre-populated ${insertResult.rowCount} new lost items`);
      }
    }
    // Mode 3: Normal - no expectations
    else {
      console.log('Normal mode: no expectations');
      // ✅ NON modificare inventory_items - lascia i dati come sono!
      // I counter vengono letti correttamente dal DB senza bisogno di UPDATE
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
