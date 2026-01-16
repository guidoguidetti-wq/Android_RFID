const pool = require('../db/config');

class Checklist {
  /**
   * Trova tutte le checklist per tipo
   * @param {string} chkType - Tipo checklist (es: 'CHI')
   * @returns {Promise<Array>} Lista checklist
   */
  static async findByType(chkType) {
    const result = await pool.query(
      `SELECT chk_id, chk_code, chk_place, chk_zone, chk_notes, chk_creationdate
       FROM checklist
       WHERE chk_type = $1
       ORDER BY chk_code`,
      [chkType]
    );
    console.log(`Found ${result.rows.length} checklists of type '${chkType}'`);
    return result.rows;
  }

  /**
   * Trova una checklist per ID
   * @param {number} chkId - Checklist ID
   * @returns {Promise<Object|null>} Checklist
   */
  static async findById(chkId) {
    const result = await pool.query(
      `SELECT * FROM checklist WHERE chk_id = $1`,
      [chkId]
    );
    return result.rows[0] || null;
  }

  /**
   * Ottieni tutte le checklist
   * @returns {Promise<Array>} Lista checklist
   */
  static async getAll() {
    const result = await pool.query(
      `SELECT chk_id, chk_code, chk_type, chk_place, chk_zone, chk_notes, chk_creationdate
       FROM checklist
       ORDER BY chk_type, chk_code`
    );
    return result.rows;
  }

  /**
   * Crea una nuova checklist
   * @param {Object} data - Dati checklist
   * @returns {Promise<Object>} Checklist creata
   */
  static async create(data) {
    const { code, type, place, zone, notes } = data;
    const result = await pool.query(
      `INSERT INTO checklist (chk_code, chk_type, chk_place, chk_zone, chk_notes, chk_creationdate)
       VALUES ($1, $2, $3, $4, $5, CURRENT_DATE)
       RETURNING *`,
      [code, type, place, zone, notes]
    );
    return result.rows[0];
  }
}

module.exports = Checklist;
