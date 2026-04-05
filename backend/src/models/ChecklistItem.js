const pool = require('../db/config');

/**
 * Model per gestire checklist_items
 * Usato per la logica Expected/Unexpected/Lost negli inventari di tipo 3 (Checklist EPC)
 */
class ChecklistItem {
  /**
   * Ottiene tutti gli EPC di una checklist con le quantità
   * @param {number} chkId - ID della checklist
   * @returns {Promise<Array>} Lista di {ckp_epc_id, ckp_qta, ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing}
   */
  static async getByChecklistId(chkId) {
    const result = await pool.query(
      `SELECT ckp_epc_id, ckp_qta, ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing
       FROM checklist_items
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
       FROM checklist_items
       WHERE ckp_chl_id = $1`,
      [chkId]
    );
    return parseInt(result.rows[0].total);
  }

  /**
   * Verifica se un EPC è presente nella checklist
   * @param {number} chkId - ID della checklist
   * @param {string} epc - EPC da cercare
   * @returns {Promise<Object|null>} Record checklist_item o null
   */
  static async findEpcInChecklist(chkId, epc) {
    const result = await pool.query(
      `SELECT ckp_epc_id, ckp_qta, ckp_qta_exp, ckp_qta_unexp, ckp_qta_missing
       FROM checklist_items
       WHERE ckp_chl_id = $1 AND ckp_epc_id = $2`,
      [chkId, epc]
    );
    return result.rows[0] || null;
  }

  /**
   * Incrementa il counter ckp_qta_exp (Expected) per un EPC
   * @param {number} chkId - ID della checklist
   * @param {string} epc - EPC
   */
  static async incrementExpected(chkId, epc) {
    await pool.query(
      `UPDATE checklist_items
       SET ckp_qta_exp = COALESCE(ckp_qta_exp, 0) + 1
       WHERE ckp_chl_id = $1 AND ckp_epc_id = $2`,
      [chkId, epc]
    );
  }

  /**
   * Incrementa il counter ckp_qta_unexp (Unexpected) per un EPC
   * @param {number} chkId - ID della checklist
   * @param {string} epc - EPC
   */
  static async incrementUnexpected(chkId, epc) {
    await pool.query(
      `UPDATE checklist_items
       SET ckp_qta_unexp = COALESCE(ckp_qta_unexp, 0) + 1
       WHERE ckp_chl_id = $1 AND ckp_epc_id = $2`,
      [chkId, epc]
    );
  }

  /**
   * Azzera i counter per una checklist
   * @param {number} chkId - ID della checklist
   */
  static async resetCounters(chkId) {
    await pool.query(
      `UPDATE checklist_items
       SET ckp_qta_exp = 0,
           ckp_qta_unexp = 0,
           ckp_qta_missing = ckp_qta
       WHERE ckp_chl_id = $1`,
      [chkId]
    );
  }

  /**
   * Sincronizza i counter della checklist basandosi sui dati reali in inventory_items
   * @param {number} chkId - ID della checklist
   * @param {number} invId - ID dell'inventario
   */
  static async syncCountersWithInventory(chkId, invId) {
    const items = await pool.query(
      `SELECT ckp_epc_id, ckp_qta FROM checklist_items WHERE ckp_chl_id = $1`,
      [chkId]
    );

    for (const item of items.rows) {
      const found = await pool.query(
        `SELECT inv_expected, inv_unexpected FROM "inventory_items"
         WHERE int_inv_id = $1 AND int_epc = $2`,
        [invId, item.ckp_epc_id]
      );

      const isExpected = found.rows[0]?.inv_expected === true ? 1 : 0;
      const isUnexpected = found.rows[0]?.inv_unexpected === true ? 1 : 0;
      const total = parseInt(item.ckp_qta) || 0;
      const missing = Math.max(0, total - isExpected);

      await pool.query(
        `UPDATE checklist_items
         SET ckp_qta_exp = $1, ckp_qta_unexp = $2, ckp_qta_missing = $3
         WHERE ckp_chl_id = $4 AND ckp_epc_id = $5`,
        [isExpected, isUnexpected, missing, chkId, item.ckp_epc_id]
      );
    }
  }
}

module.exports = ChecklistItem;
