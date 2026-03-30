const pool = require('../db/config');

class Inventory {
  /**
   * Trova tutti gli inventari aperti per un Place specifico
   * @param {number} placeId - Place ID (integer)
   * @returns {Promise<Array>} Lista inventari aperti con conteggio items
   */
  static async findOpenByPlace(placeId) {
    const result = await pool.query(
      `SELECT i.*,
         (SELECT COUNT(*) FROM "inventory_items" WHERE int_inv_id = i.inv_id AND (inv_lost = false OR inv_lost IS NULL)) as items_count
       FROM "inventories" i
       WHERE UPPER(i.inv_state) = 'OPEN' AND i.inv_place_id = $1
       ORDER BY i.inv_start_date DESC`,
      [placeId]
    );
    console.log(`Found ${result.rows.length} open inventories for place ID '${placeId}'`);
    return result.rows;
  }

  /**
   * Trova un inventario per ID con dettagli completi
   * @param {number} invId - Inventory ID (integer)
   * @returns {Promise<Object|null>} Inventario con dettagli
   */
  static async findById(invId) {
    const result = await pool.query(
      `SELECT i.*,
         (SELECT COUNT(*) FROM "inventory_items" WHERE int_inv_id = i.inv_id AND (inv_lost = false OR inv_lost IS NULL)) as items_count
       FROM "inventories" i
       WHERE i.inv_id = $1`,
      [invId]
    );
    return result.rows[0] || null;
  }

  /**
   * Trova tutti gli inventari per un utente
   * @param {string} userId - User ID
   * @returns {Promise<Array>} Lista inventari utente
   */
  static async findByUser(userId) {
    const result = await pool.query(
      `SELECT i.*,
         p.place_name,
         (SELECT COUNT(*) FROM "inventory_items" WHERE inventory_id = i.inv_id) as items_count
       FROM "inventories" i
       LEFT JOIN "Places" p ON i.inv_place_id = p.place_id
       WHERE i.inv_user = $1
       ORDER BY i.inv_start_date DESC`,
      [userId]
    );
    return result.rows;
  }

  /**
   * Crea un nuovo inventario
   * @param {Object} invData - Dati inventario
   * @param {string} invData.name - Nome inventario
   * @param {string} invData.note - Note
   * @param {string} invData.placeId - Place ID
   * @param {number} invData.chkId - Checklist ID (0 se non da checklist)
   * @param {string} invData.zones - Zone IDs separate da virgola (es: "Z1,Z2,Z3") - DEPRECATED, usa invLastZones
   * @param {string} invData.detPlace - Place per detected items
   * @param {string} invData.detZone - Zone per detected items
   * @param {string} invData.misPlace - Place per missed items
   * @param {string} invData.misZone - Zone per missed items
   * @param {boolean} invData.invLast - true se inventario da giacenza RFID
   * @param {string} invData.invLastPlace - Place per inventario da giacenza
   * @param {string} invData.invLastZones - Zone per inventario da giacenza (separate da virgola)
   * @returns {Promise<Object>} Inventario creato (con inv_id auto-generato)
   */
  static async create(invData) {
    const {
      name,
      note,
      placeId,
      chkId = null,
      zones = null,
      detPlace = null,
      detZone = null,
      misPlace = null,
      misZone = null,
      invLast = false,
      invLastPlace = null,
      invLastZones = null
    } = invData;

    const result = await pool.query(
      `INSERT INTO "inventories" (
        inv_name, inv_note, inv_place_id, inv_state, inv_start_date,
        inv_chk_id, inv_det_place, inv_det_zone, inv_mis_place, inv_mis_zone,
        inv_last, inv_last_place, inv_last_zones
      )
       VALUES ($1, $2, $3, 'OPEN', CURRENT_DATE, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING *`,
      [name, note, placeId, chkId, detPlace, detZone, misPlace, misZone, invLast, invLastPlace, invLastZones]
    );
    console.log(`Inventory '${result.rows[0].inv_id}' created for place '${placeId}' (chkId: ${chkId}, invLast: ${invLast}, lastZones: ${invLastZones})`);
    return result.rows[0];
  }

  /**
   * Aggiorna lo stato di un inventario
   * @param {string} invId - Inventory ID
   * @param {string} state - Nuovo stato ('OPEN' o 'CLOSE')
   * @returns {Promise<Object>} Inventario aggiornato
   */
  static async updateState(invId, state) {
    const result = await pool.query(
      `UPDATE "inventories"
       SET inv_state = $2
       WHERE inv_id = $1 RETURNING *`,
      [invId, state]
    );
    console.log(`Inventory '${invId}' state updated to '${state}'`);
    return result.rows[0];
  }

  /**
   * Aggiorna i dati di un inventario
   * @param {string} invId - Inventory ID
   * @param {Object} updates - Campi da aggiornare
   * @returns {Promise<Object>} Inventario aggiornato
   */
  static async update(invId, updates) {
    const fields = [];
    const values = [];
    let paramIndex = 1;

    Object.entries(updates).forEach(([key, value]) => {
      if (value !== undefined) {
        fields.push(`${key} = $${paramIndex}`);
        values.push(value);
        paramIndex++;
      }
    });

    if (fields.length === 0) {
      throw new Error('No fields to update');
    }

    values.push(invId);
    const result = await pool.query(
      `UPDATE "inventories" SET ${fields.join(', ')} WHERE inv_id = $${paramIndex} RETURNING *`,
      values
    );

    return result.rows[0];
  }

  /**
   * Elimina un inventario (e tutti i suoi items per CASCADE)
   * @param {string} invId - Inventory ID
   * @returns {Promise<boolean>} True se eliminato
   */
  static async delete(invId) {
    const result = await pool.query(
      'DELETE FROM "inventories" WHERE inv_id = $1 RETURNING *',
      [invId]
    );
    console.log(`Inventory '${invId}' deleted`);
    return result.rowCount > 0;
  }

  /**
   * Ottieni tutti gli inventari con filtri opzionali
   * @param {Object} filters - Filtri opzionali
   * @param {string} filters.state - Filtra per stato
   * @param {string} filters.placeId - Filtra per place
   * @param {number} filters.limit - Limite risultati
   * @param {number} filters.offset - Offset risultati
   * @returns {Promise<Array>} Lista inventari
   */
  static async getAll(filters = {}) {
    let query = `
      SELECT i.*,
        (SELECT COUNT(*) FROM "inventory_items" WHERE int_inv_id = i.inv_id AND (inv_lost = false OR inv_lost IS NULL)) as items_count
      FROM "inventories" i
      WHERE 1=1
    `;

    const values = [];
    let paramIndex = 1;

    if (filters.state) {
      query += ` AND i.inv_state = $${paramIndex}`;
      values.push(filters.state);
      paramIndex++;
    }

    if (filters.placeId) {
      query += ` AND i.inv_place_id = $${paramIndex}`;
      values.push(filters.placeId);
      paramIndex++;
    }

    query += ' ORDER BY i.inv_start_date DESC';

    if (filters.limit) {
      query += ` LIMIT $${paramIndex}`;
      values.push(filters.limit);
      paramIndex++;
    }

    if (filters.offset) {
      query += ` OFFSET $${paramIndex}`;
      values.push(filters.offset);
    }

    const result = await pool.query(query, values);
    return result.rows;
  }
}

module.exports = Inventory;
