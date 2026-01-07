const pool = require('../db/config');

class InventoryItem {
  /**
   * Aggiungi un item scannerizzato a un inventario
   * @param {number} inventoryId - Inventory ID (integer)
   * @param {string} epc - EPC del tag scannerizzato
   * @returns {Promise<Object|null>} Item aggiunto o null se già esistente
   */
  static async addItem(inventoryId, epc) {
    try {
      // Check if already exists
      const checkResult = await pool.query(
        'SELECT * FROM "inventory_items" WHERE int_inv_id = $1 AND int_epc = $2',
        [inventoryId, epc]
      );

      if (checkResult.rows.length > 0) {
        console.log(`Item EPC '${epc}' already exists in inventory '${inventoryId}' - skipped`);
        return null;
      }

      // Insert new item
      const result = await pool.query(
        `INSERT INTO "inventory_items" (int_inv_id, int_epc)
         VALUES ($1, $2)
         RETURNING *`,
        [inventoryId, epc]
      );

      console.log(`Item EPC '${epc}' added to inventory '${inventoryId}'`);
      return result.rows[0];
    } catch (error) {
      console.error('Error adding item to inventory:', error);
      throw error;
    }
  }

  /**
   * Ottieni tutti gli items di un inventario con dettagli
   * @param {string} inventoryId - Inventory ID
   * @returns {Promise<Array>} Lista items con dettagli da tabella Items
   */
  static async getItemsByInventory(inventoryId) {
    const result = await pool.query(
      `SELECT ii.*,
         i.item_id as epc,
         i.place_last,
         i.zone_last,
         i.item_product_id,
         i.date_lastseen
       FROM "inventory_items" ii
       LEFT JOIN "Items" i ON ii.int_epc = i.item_id
       WHERE ii.int_inv_id = $1
       ORDER BY ii.scan_timestamp DESC`,
      [inventoryId]
    );
    return result.rows;
  }

  /**
   * Conta gli items in un inventario
   * @param {number} inventoryId - Inventory ID (integer)
   * @returns {Promise<number>} Conteggio items
   */
  static async getCountByInventory(inventoryId) {
    const result = await pool.query(
      'SELECT COUNT(*) as count FROM "inventory_items" WHERE int_inv_id = $1',
      [inventoryId]
    );
    return parseInt(result.rows[0].count);
  }

  /**
   * Verifica se un EPC è già presente in un inventario
   * @param {string} inventoryId - Inventory ID
   * @param {string} epc - EPC da verificare
   * @returns {Promise<boolean>} True se presente
   */
  static async existsInInventory(inventoryId, epc) {
    const result = await pool.query(
      'SELECT 1 FROM "inventory_items" WHERE int_inv_id = $1 AND int_epc = $2',
      [inventoryId, epc]
    );
    return result.rows.length > 0;
  }

  /**
   * Rimuovi un item da un inventario
   * @param {string} inventoryId - Inventory ID
   * @param {string} epc - EPC da rimuovere
   * @returns {Promise<boolean>} True se rimosso
   */
  static async removeItem(inventoryId, epc) {
    const result = await pool.query(
      'DELETE FROM "inventory_items" WHERE int_inv_id = $1 AND int_epc = $2 RETURNING *',
      [inventoryId, epc]
    );
    if (result.rowCount > 0) {
      console.log(`Item EPC '${epc}' removed from inventory '${inventoryId}'`);
      return true;
    }
    return false;
  }

  /**
   * Ottieni statistiche di un inventario
   * @param {string} inventoryId - Inventory ID
   * @returns {Promise<Object>} Statistiche (total, by_place, by_zone, etc.)
   */
  static async getInventoryStats(inventoryId) {
    const result = await pool.query(
      `SELECT
         COUNT(*) as total_items,
         COUNT(DISTINCT i.place_last) as unique_places,
         COUNT(DISTINCT i.zone_last) as unique_zones,
         COUNT(DISTINCT i.item_product_id) as unique_products,
         MIN(ii.scan_timestamp) as first_scan,
         MAX(ii.scan_timestamp) as last_scan
       FROM "inventory_items" ii
       LEFT JOIN "Items" i ON ii.int_epc = i.item_id
       WHERE ii.int_inv_id = $1`,
      [inventoryId]
    );
    return result.rows[0];
  }

  /**
   * Ottieni raggruppamento items per place
   * @param {string} inventoryId - Inventory ID
   * @returns {Promise<Array>} Items raggruppati per place
   */
  static async getItemsByPlace(inventoryId) {
    const result = await pool.query(
      `SELECT i.place_last, p.place_name, COUNT(*) as count
       FROM "inventory_items" ii
       LEFT JOIN "Items" i ON ii.int_epc = i.item_id
       LEFT JOIN "Places" p ON i.place_last = p.place_id
       WHERE ii.int_inv_id = $1
       GROUP BY i.place_last, p.place_name
       ORDER BY count DESC`,
      [inventoryId]
    );
    return result.rows;
  }

  /**
   * Ottieni raggruppamento items per zone
   * @param {string} inventoryId - Inventory ID
   * @returns {Promise<Array>} Items raggruppati per zone
   */
  static async getItemsByZone(inventoryId) {
    const result = await pool.query(
      `SELECT i.zone_last, z.zone_name, COUNT(*) as count
       FROM "inventory_items" ii
       LEFT JOIN "Items" i ON ii.int_epc = i.item_id
       LEFT JOIN "Zones" z ON i.zone_last = z.zone_id
       WHERE ii.int_inv_id = $1
       GROUP BY i.zone_last, z.zone_name
       ORDER BY count DESC`,
      [inventoryId]
    );
    return result.rows;
  }

  /**
   * Svuota un inventario (rimuovi tutti gli items)
   * @param {string} inventoryId - Inventory ID
   * @returns {Promise<number>} Numero di items rimossi
   */
  static async clearInventory(inventoryId) {
    const result = await pool.query(
      'DELETE FROM "inventory_items" WHERE int_inv_id = $1',
      [inventoryId]
    );
    console.log(`Inventory '${inventoryId}' cleared - ${result.rowCount} items removed`);
    return result.rowCount;
  }
}

module.exports = InventoryItem;
