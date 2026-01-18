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
       WHERE ckp_chk_id = $1`,
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
       WHERE ckp_chk_id = $1`,
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
      `SELECT ckp_product_id, ckp_qta
       FROM checklist_products
       WHERE ckp_chk_id = $1 AND ckp_product_id = $2`,
      [chkId, productId]
    );
    return result.rows[0] || null;
  }
}

module.exports = ChecklistProduct;
