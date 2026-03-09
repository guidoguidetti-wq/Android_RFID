const pool = require('../db/config');

/**
 * Model per gestire checklist_products
 * Usato per la logica Expected/Unexpected/Lost negli inventari da checklist
 */
class ChecklistProduct {
  /**
   * Ottiene tutti i prodotti di una checklist con le quantità
   * @param {number} chkId - ID della checklist
   * @returns {Promise<Array>} Lista di {ckp_product_id, ckp_qta}
   */
  static async getByChecklistId(chkId) {
    const result = await pool.query(
      `SELECT ckp_product_id, ckp_qta, ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing
       FROM checklist_products
       WHERE ckp_chl_id = $1`,
      [chkId]
    );
    return result.rows;
  }

  /**
   * Ottiene la somma totale delle quantità attese dalla checklist
   * @param {number} chkId - ID della checklist
   * @returns {Promise<number>} Somma di ckp_qta
   */
  static async getTotalQuantity(chkId) {
    const result = await pool.query(
      `SELECT COALESCE(SUM(ckp_qta), 0) as total
       FROM checklist_products
       WHERE ckp_chl_id = $1`,
      [chkId]
    );
    return parseInt(result.rows[0].total);
  }

  /**
   * Verifica se un product_id è presente nella checklist
   * @param {number} chkId - ID della checklist
   * @param {string} productId - ID del prodotto
   * @returns {Promise<Object|null>} Record checklist_product o null
   */
  static async findProductInChecklist(chkId, productId) {
    const result = await pool.query(
      `SELECT ckp_product_id, ckp_qta, ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing
       FROM checklist_products
       WHERE ckp_chl_id = $1 AND ckp_product_id = $2`,
      [chkId, productId]
    );
    return result.rows[0] || null;
  }

  /**
   * Incrementa il counter ckp_qta_exp (Expected) per un prodotto
   * @param {number} chkId - ID della checklist
   * @param {string} productId - ID del prodotto
   * @returns {Promise<Object>} Record aggiornato
   */
  static async incrementExpected(chkId, productId) {
    const result = await pool.query(
      `UPDATE checklist_products
       SET ckp_qta_exp = COALESCE(ckp_qta_exp, 0) + 1
       WHERE ckp_chl_id = $1 AND ckp_product_id = $2
       RETURNING *`,
      [chkId, productId]
    );
    return result.rows[0];
  }

  /**
   * Incrementa il counter ckp_qta_unexp (Unexpected) per un prodotto
   * @param {number} chkId - ID della checklist
   * @param {string} productId - ID del prodotto
   * @returns {Promise<Object>} Record aggiornato
   */
  static async incrementUnexpected(chkId, productId) {
    const result = await pool.query(
      `UPDATE checklist_products
       SET ckp_qta_unexp = COALESCE(ckp_qta_unexp, 0) + 1
       WHERE ckp_chl_id = $1 AND ckp_product_id = $2
       RETURNING *`,
      [chkId, productId]
    );
    return result.rows[0];
  }

  /**
   * Decrementa il counter ckp_qta_missing (Lost) per un prodotto
   * @param {number} chkId - ID della checklist
   * @param {string} productId - ID del prodotto
   * @returns {Promise<Object>} Record aggiornato
   */
  static async decrementMissing(chkId, productId) {
    const result = await pool.query(
      `UPDATE checklist_products
       SET ckp_qta_missing = GREATEST(COALESCE(ckp_qta_missing, 0) - 1, 0)
       WHERE ckp_chl_id = $1 AND ckp_product_id = $2
       RETURNING *`,
      [chkId, productId]
    );
    return result.rows[0];
  }

  /**
   * Azzera i counter ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing per una checklist
   * Usato all'inizio di un nuovo inventario
   * @param {number} chkId - ID della checklist
   * @returns {Promise<void>}
   */
  static async resetCounters(chkId) {
    await pool.query(
      `UPDATE checklist_products
       SET ckp_qta_exp = 0,
           ckp_qta_unexp = 0,
           ckp_qta_missing = ckp_qta
       WHERE ckp_chl_id = $1`,
      [chkId]
    );
  }

  /**
   * Sincronizza i counter della checklist basandosi sui dati reali in inventory_items
   * Usato quando si riapre un inventario esistente
   * @param {number} chkId - ID della checklist
   * @param {number} invId - ID dell'inventario
   * @returns {Promise<void>}
   */
  static async syncCountersWithInventory(chkId, invId) {
    // Per ogni prodotto nella checklist, conta gli item con quel product_id nell'inventario
    const result = await pool.query(
      `SELECT
        cp.ckp_product_id,
        cp.ckp_qta,
        COUNT(CASE WHEN ii.inv_expected = true THEN 1 END) as current_exp,
        COUNT(CASE WHEN ii.inv_unexpected = true THEN 1 END) as current_unexp
       FROM checklist_products cp
       LEFT JOIN "Items" i ON i.item_product_id = cp.ckp_product_id
       LEFT JOIN "inventory_items" ii ON ii.int_epc = i.item_id AND ii.int_inv_id = $2
       WHERE cp.ckp_chl_id = $1
       GROUP BY cp.ckp_product_id, cp.ckp_qta`,
      [chkId, invId]
    );

    // Aggiorna ogni prodotto con i valori reali
    for (const row of result.rows) {
      const expected = parseInt(row.current_exp) || 0;
      const unexpected = parseInt(row.current_unexp) || 0;
      const total = parseInt(row.ckp_qta) || 0;
      const missing = Math.max(0, total - expected);

      await pool.query(
        `UPDATE checklist_products
         SET ckp_qta_exp = $1,
             ckp_qta_unexp = $2,
             ckp_qta_missing = $3
         WHERE ckp_chl_id = $4 AND ckp_product_id = $5`,
        [expected, unexpected, missing, chkId, row.ckp_product_id]
      );
    }
  }
}

module.exports = ChecklistProduct;
