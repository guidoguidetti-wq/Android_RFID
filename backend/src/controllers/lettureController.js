const pool = require('../db/config');

/**
 * POST /api/letture/init
 * Inizializza sessione di lettura per un utente nella tabella inventory_items_temp.
 * - Pulisce eventuale sessione precedente dello stesso utente
 * - Per modalità EPC checklist: pre-popola i lost dagli EPC attesi
 * - Per modalità SKU checklist: calcola totalExpected dalla somma delle qta
 * - Restituisce: mode, totalExpected, chkId
 */
exports.initSession = async (req, res) => {
  try {
    const { userId, checklistMode, checklistCode } = req.body;
    if (!userId) return res.status(400).json({ error: 'userId required' });

    // Pulisce sessione precedente
    await pool.query('DELETE FROM "inventory_items_temp" WHERE int_id = $1', [userId]);

    let totalExpected = 0;
    let mode = 'normal';
    let chkId = null;

    if ((checklistMode === 'sku' || checklistMode === 'epc') && checklistCode) {
      const chkRes = await pool.query(
        'SELECT chk_id FROM checklist WHERE chk_code = $1 LIMIT 1',
        [checklistCode]
      );
      if (chkRes.rows.length === 0) {
        return res.status(404).json({ error: `Checklist '${checklistCode}' non trovata` });
      }
      chkId = chkRes.rows[0].chk_id;

      if (checklistMode === 'epc') {
        const insertRes = await pool.query(
          `INSERT INTO "inventory_items_temp" (int_id, int_inv_id, int_epc, inv_lost, inv_expected, inv_unexpected)
           SELECT $1, 0, ckp_epc_id, true, false, false
           FROM checklist_items WHERE ckp_chl_id = $2`,
          [userId, chkId]
        );
        totalExpected = insertRes.rowCount;
        mode = 'checklist_epc';
      } else {
        const qtaRes = await pool.query(
          'SELECT COALESCE(SUM(ckp_qta), 0) as total FROM checklist_products WHERE ckp_chl_id = $1',
          [chkId]
        );
        totalExpected = parseInt(qtaRes.rows[0].total) || 0;
        mode = 'checklist';
      }
    }

    console.log(`Letture session init: userId=${userId}, mode=${mode}, totalExpected=${totalExpected}, chkId=${chkId}`);
    res.json({ success: true, mode, totalExpected, chkId });
  } catch (error) {
    console.error('Error in letture init:', error);
    res.status(500).json({ error: 'Failed to init session', details: error.message });
  }
};

/**
 * GET /api/letture/counters?userId=xxx
 * Contatori correnti della sessione temp dell'utente.
 */
exports.getCounters = async (req, res) => {
  try {
    const { userId } = req.query;
    if (!userId) return res.status(400).json({ error: 'userId required' });

    const result = await pool.query(
      `SELECT
        COUNT(*) as total_count,
        COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
        COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
        COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items_temp" WHERE int_id = $1`,
      [userId]
    );

    res.json({
      total_count:      parseInt(result.rows[0].total_count)      || 0,
      expected_count:   parseInt(result.rows[0].expected_count)   || 0,
      unexpected_count: parseInt(result.rows[0].unexpected_count) || 0,
      lost_count:       parseInt(result.rows[0].lost_count)       || 0
    });
  } catch (error) {
    console.error('Error getting letture counters:', error);
    res.status(500).json({ error: 'Failed to get counters', details: error.message });
  }
};

/**
 * POST /api/letture/batch-scan
 * Processa un batch di EPCs nella sessione temp.
 * Stessa logica di inventories/batch-scan ma usa inventory_items_temp con int_id (userId).
 */
exports.batchScan = async (req, res) => {
  try {
    const { userId, epcs, checklistMode, chkId, mode, placeId, zoneId } = req.body;

    if (!userId || !epcs || !Array.isArray(epcs) || epcs.length === 0) {
      return res.status(400).json({ error: 'userId and epcs array required' });
    }

    console.log(`Letture batch scan: userId=${userId}, ${epcs.length} EPCs, checklistMode=${checklistMode}, mode=${mode}`);

    const isChecklistSKU = checklistMode === 'checklist';
    const isChecklistEPC = checklistMode === 'checklist_epc';
    const isNormal       = !isChecklistSKU && !isChecklistEPC;

    // 1. EPCs già presenti in temp
    const existingRes = await pool.query(
      `SELECT int_epc, inv_lost FROM "inventory_items_temp" WHERE int_id = $1 AND int_epc = ANY($2)`,
      [userId, epcs]
    );
    const existingMap = new Map();
    existingRes.rows.forEach(r => existingMap.set(r.int_epc, { inv_lost: r.inv_lost }));

    const epcsToProcess = epcs.filter(epc => {
      const ex = existingMap.get(epc);
      return !ex || ex.inv_lost === true;
    });

    if (epcsToProcess.length === 0) {
      const cRes = await pool.query(
        `SELECT COUNT(*) as total_count,
                COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
                COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
                COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
         FROM "inventory_items_temp" WHERE int_id = $1`,
        [userId]
      );
      return res.json({
        success: true, processedCount: 0,
        counters: {
          expectedCount:   parseInt(cRes.rows[0].expected_count)   || 0,
          unexpectedCount: parseInt(cRes.rows[0].unexpected_count) || 0,
          lostCount:       parseInt(cRes.rows[0].lost_count)       || 0,
          ignoredCount:    0
        }
      });
    }

    // 2. Dati Items per gli EPCs
    const itemsRes = await pool.query(
      `SELECT item_id, item_product_id FROM "Items" WHERE item_id = ANY($1)`,
      [epcsToProcess]
    );
    const itemsMap = new Map();
    itemsRes.rows.forEach(r => itemsMap.set(r.item_id, r));

    // 3. Setup strutture per modalità checklist
    let checklistProductsMap = null;
    let expectedEpcsSet     = null;

    if (isChecklistSKU && chkId) {
      const products = await pool.query(
        `SELECT ckp_product_id, ckp_qta, COALESCE(ckp_qta_exp, 0) as ckp_qta_exp
         FROM checklist_products WHERE ckp_chl_id = $1`,
        [chkId]
      );
      checklistProductsMap = new Map();
      products.rows.forEach(p => checklistProductsMap.set(String(p.ckp_product_id), {
        ckp_qta:     parseInt(p.ckp_qta)     || 0,
        ckp_qta_exp: parseInt(p.ckp_qta_exp) || 0
      }));
    } else if (isChecklistEPC && chkId) {
      const chkItems = await pool.query(
        `SELECT ckp_epc_id FROM checklist_items WHERE ckp_chl_id = $1`,
        [chkId]
      );
      expectedEpcsSet = new Set(chkItems.rows.map(i => String(i.ckp_epc_id)));
    }

    // 4. Classifica EPCs
    const toInsertNew    = [];
    const toUpdateLost   = [];
    const chkIncrements  = new Map();

    for (const epc of epcsToProcess) {
      const itemData  = itemsMap.get(epc);
      const productId = itemData?.item_product_id ? String(itemData.item_product_id) : null;
      const isLostUpd = existingMap.has(epc) && existingMap.get(epc).inv_lost === true;

      let status  = null;
      let shouldAdd = true;

      if (isNormal) {
        if (mode === 'mode_b') {
          status = 'expected';
        } else if (mode === 'mode_a') {
          status = itemData ? 'expected' : null;
        } else {
          status = 'expected'; // mode_c default
        }
      } else if (isChecklistEPC) {
        status = (expectedEpcsSet && expectedEpcsSet.has(epc)) ? 'expected' : 'unexpected';
      } else if (isChecklistSKU) {
        if (!itemData) {
          shouldAdd = false;
        } else if (productId && checklistProductsMap && checklistProductsMap.has(productId)) {
          const prod = checklistProductsMap.get(productId);
          const inc  = chkIncrements.get(productId) || { expected: 0, unexpected: 0 };
          if (prod.ckp_qta_exp + inc.expected + 1 <= prod.ckp_qta) {
            status = 'expected';
            inc.expected++;
          } else {
            status = 'unexpected';
            inc.unexpected++;
          }
          chkIncrements.set(productId, inc);
        } else {
          status = 'unexpected';
        }
      }

      if (!shouldAdd) continue;

      const invExpected   = status === 'expected';
      const invUnexpected = status === 'unexpected';

      if (isLostUpd) {
        toUpdateLost.push({ epc, invExpected, invUnexpected });
      } else {
        toInsertNew.push({ epc, invExpected, invUnexpected });
      }
    }

    // 5. UPDATE lost
    for (const item of toUpdateLost) {
      await pool.query(
        `UPDATE "inventory_items_temp"
         SET inv_lost = false, inv_expected = $3, inv_unexpected = $4
         WHERE int_id = $1 AND int_epc = $2`,
        [userId, item.epc, item.invExpected, item.invUnexpected]
      );
    }

    // 6. Bulk INSERT nuovi
    if (toInsertNew.length > 0) {
      const vals   = toInsertNew.map((_, i) => `($${i*4+1}, 0, $${i*4+2}, $${i*4+3}, $${i*4+4}, false)`).join(', ');
      const params = toInsertNew.flatMap(item => [userId, item.epc, item.invExpected, item.invUnexpected]);
      await pool.query(
        `INSERT INTO "inventory_items_temp" (int_id, int_inv_id, int_epc, inv_expected, inv_unexpected, inv_lost) VALUES ${vals}`,
        params
      );
    }

    // 7. Aggiorna counter checklist SKU
    if (isChecklistSKU && chkId) {
      for (const [productId, inc] of chkIncrements) {
        if (inc.expected > 0) {
          await pool.query(
            `UPDATE checklist_products SET ckp_qta_exp = ckp_qta_exp + $1
             WHERE ckp_chl_id = $2 AND ckp_product_id = $3`,
            [inc.expected, chkId, productId]
          );
        }
        if (inc.unexpected > 0) {
          await pool.query(
            `UPDATE checklist_products SET ckp_qta_unexp = ckp_qta_unexp + $1
             WHERE ckp_chl_id = $2 AND ckp_product_id = $3`,
            [inc.unexpected, chkId, productId]
          );
        }
      }
    }

    // 8. Contatori finali
    const cRes = await pool.query(
      `SELECT COUNT(*) as total_count,
              COUNT(CASE WHEN inv_expected = true THEN 1 END) as expected_count,
              COUNT(CASE WHEN inv_unexpected = true THEN 1 END) as unexpected_count,
              COUNT(CASE WHEN inv_lost = true THEN 1 END) as lost_count
       FROM "inventory_items_temp" WHERE int_id = $1`,
      [userId]
    );
    const bTotal      = parseInt(cRes.rows[0].total_count)      || 0;
    const bExpected   = parseInt(cRes.rows[0].expected_count)   || 0;
    const bUnexpected = parseInt(cRes.rows[0].unexpected_count) || 0;
    const bLost       = parseInt(cRes.rows[0].lost_count)       || 0;

    const processedCount = toInsertNew.length + toUpdateLost.length;
    console.log(`Letture batch done: ${processedCount} processed, E:${bExpected} U:${bUnexpected} L:${bLost}`);

    res.json({
      success: true,
      processedCount,
      counters: {
        expectedCount:   bExpected,
        unexpectedCount: bUnexpected,
        lostCount:       bLost,
        ignoredCount:    Math.max(0, bTotal - bExpected - bUnexpected - bLost)
      }
    });
  } catch (error) {
    console.error('Error in letture batch-scan:', error);
    res.status(500).json({ error: 'Failed to process batch scan', details: error.message });
  }
};

/**
 * GET /api/letture/items?userId=xxx
 * Restituisce gli EPCs della sessione temp con status e info prodotto.
 */
exports.getItems = async (req, res) => {
  try {
    const { userId } = req.query;
    if (!userId) return res.status(400).json({ error: 'userId required' });

    const result = await pool.query(
      `SELECT
         t.int_epc        AS epc,
         t.inv_expected,
         t.inv_unexpected,
         t.inv_lost,
         i.item_product_id AS product_id,
         p.fld01, p.fld02, p.fld03, p.fldd01
       FROM "inventory_items_temp" t
       LEFT JOIN "Items"    i ON t.int_epc = i.item_id
       LEFT JOIN "Products" p ON i.item_product_id = p.product_id
       WHERE t.int_id = $1
       ORDER BY t.inv_lost DESC, t.inv_unexpected DESC, t.int_epc`,
      [userId]
    );

    res.json(result.rows);
  } catch (error) {
    console.error('Error getting letture items:', error);
    res.status(500).json({ error: 'Failed to get items', details: error.message });
  }
};

/**
 * DELETE /api/letture/clear?userId=xxx
 * Pulisce la sessione temp dell'utente (reset counters).
 */
exports.clearSession = async (req, res) => {
  try {
    const { userId, checklistMode, chkId } = req.query;
    if (!userId) return res.status(400).json({ error: 'userId required' });

    // Reset counter SKU checklist se necessario
    if (checklistMode === 'checklist' && chkId) {
      await pool.query(
        `UPDATE checklist_products SET ckp_qta_exp = 0, ckp_qta_unexp = 0
         WHERE ckp_chl_id = $1`,
        [chkId]
      );
    }

    const result = await pool.query(
      'DELETE FROM "inventory_items_temp" WHERE int_id = $1',
      [userId]
    );

    // Re-popola lost per modalità EPC se chkId fornito
    let repopulated = 0;
    if (checklistMode === 'checklist_epc' && chkId) {
      const insRes = await pool.query(
        `INSERT INTO "inventory_items_temp" (int_id, int_inv_id, int_epc, inv_lost, inv_expected, inv_unexpected)
         SELECT $1, 0, ckp_epc_id, true, false, false
         FROM checklist_items WHERE ckp_chl_id = $2`,
        [userId, chkId]
      );
      repopulated = insRes.rowCount;
    }

    res.json({ success: true, itemsRemoved: result.rowCount, itemsRepopulated: repopulated });
  } catch (error) {
    console.error('Error clearing letture session:', error);
    res.status(500).json({ error: 'Failed to clear session', details: error.message });
  }
};

/**
 * POST /api/letture/commit
 * Conferma la sessione:
 * - Se registraTransferimento=true: INSERT Movements con dest_place/zone + UPDATE Items.place_last
 * - Altrimenti: INSERT Movements come letture semplici + aggiorna solo date_lastseen
 * Poi pulisce inventory_items_temp per l'utente.
 */
exports.commitSession = async (req, res) => {
  try {
    const {
      userId,
      registraTransferimento,
      destPlaceId,
      destZoneId,
      riferimento,
      note,
      readerName,
      registraSoloExpected = true
    } = req.body;

    if (!userId) return res.status(400).json({ error: 'userId required' });

    // Recupera EPCs scansionati in base al filtro richiesto:
    // - registraSoloExpected=true  → solo inv_expected=true
    // - registraSoloExpected=false → inv_expected=true OR inv_unexpected=true
    // Se non è un trasferimento da checklist, si prendono tutti quelli non lost.
    let epcsQuery;
    let epcsParams;
    if (registraTransferimento && !registraSoloExpected) {
      // Trasferimento: Expected + Unexpected
      epcsQuery  = `SELECT int_epc FROM "inventory_items_temp" WHERE int_id = $1 AND (inv_expected = true OR inv_unexpected = true)`;
      epcsParams = [userId];
    } else if (registraTransferimento) {
      // Trasferimento: solo Expected (default)
      epcsQuery  = `SELECT int_epc FROM "inventory_items_temp" WHERE int_id = $1 AND inv_expected = true`;
      epcsParams = [userId];
    } else {
      // Sola lettura: tutti i tag letti (non lost)
      epcsQuery  = `SELECT int_epc FROM "inventory_items_temp" WHERE int_id = $1 AND inv_lost = false`;
      epcsParams = [userId];
    }
    const epcsRes = await pool.query(epcsQuery, epcsParams);
    const epcs = epcsRes.rows.map(r => r.int_epc);

    if (epcs.length === 0) {
      await pool.query('DELETE FROM "inventory_items_temp" WHERE int_id = $1', [userId]);
      return res.json({ success: true, processedCount: 0, message: 'Nessun tag da confermare' });
    }

    const now    = new Date();
    const reader = readerName || 'LETTURE';

    if (registraTransferimento && destPlaceId && destZoneId) {
      // Trasferimento: inserisce movimenti + aggiorna place/zone su Items
      const movVals = epcs.map((_, i) => {
        const b = i * 8;
        return `($${b+1}, $${b+2}, $${b+3}, $${b+4}, $${b+5}, $${b+6}, $${b+7}, $${b+8})`;
      }).join(', ');
      const movParams = epcs.flatMap(epc => [
        epc, destPlaceId, destZoneId, now, userId, reader, riferimento || null, note || null
      ]);
      await pool.query(
        `INSERT INTO "Movements"
           (mov_epc, mov_dest_place, mov_dest_zone, mov_timestamp, mov_user, mov_reader, mov_ref, mov_notes)
         VALUES ${movVals}`,
        movParams
      );
      await pool.query(
        `UPDATE "Items" SET place_last = $1, zone_last = $2, date_lastseen = $3
         WHERE item_id = ANY($4)`,
        [destPlaceId, destZoneId, now, epcs]
      );
    } else {
      // Sola lettura: inserisce solo i movimenti, aggiorna date_lastseen
      const movVals = epcs.map((_, i) => {
        const b = i * 5;
        return `($${b+1}, $${b+2}, $${b+3}, $${b+4}, $${b+5})`;
      }).join(', ');
      const movParams = epcs.flatMap(epc => [epc, now, userId, reader, note || null]);
      await pool.query(
        `INSERT INTO "Movements" (mov_epc, mov_timestamp, mov_user, mov_reader, mov_notes)
         VALUES ${movVals}`,
        movParams
      );
      await pool.query(
        `UPDATE "Items" SET date_lastseen = $1 WHERE item_id = ANY($2)`,
        [now, epcs]
      );
    }

    // Pulisce sessione temp
    await pool.query('DELETE FROM "inventory_items_temp" WHERE int_id = $1', [userId]);

    console.log(`Letture commit: userId=${userId}, ${epcs.length} EPCs, transfer=${registraTransferimento}, soloExpected=${registraSoloExpected}`);
    res.json({ success: true, processedCount: epcs.length });
  } catch (error) {
    console.error('Error in letture commit:', error);
    res.status(500).json({ error: 'Failed to commit session', details: error.message });
  }
};
