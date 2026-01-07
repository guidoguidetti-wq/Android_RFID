const pool = require('../db/config');

class Item {
  // Get item by EPC
  static async findByEpc(epc) {
    const result = await pool.query(
      'SELECT * FROM "Items" WHERE item_id = $1',
      [epc]
    );
    return result.rows[0];
  }

  // Get all items
  static async findAll(limit = 100, offset = 0) {
    const result = await pool.query(
      `SELECT i.*, p.fld01 as product_name
       FROM "Items" i
       LEFT JOIN "Products" p ON i.item_product_id = p.product_id
       ORDER BY i.date_lastseen DESC NULLS LAST
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    return result.rows;
  }

  // Create or update item
  static async upsert(itemData) {
    const {
      epc,
      tid = null,
      placeId = null,
      zoneId = null,
      productId = null,
      nfcUid = null
    } = itemData;

    const result = await pool.query(
      `INSERT INTO "Items" (
        item_id, tid, date_creation, date_lastseen,
        place_last, zone_last, item_product_id, nfc_uid
      )
      VALUES ($1, $2, NOW(), NOW(), $3, $4, $5, $6)
      ON CONFLICT (item_id)
      DO UPDATE SET
        date_lastseen = NOW(),
        place_last = COALESCE($3, "Items".place_last),
        zone_last = COALESCE($4, "Items".zone_last),
        tid = COALESCE($2, "Items".tid),
        item_product_id = COALESCE($5, "Items".item_product_id),
        nfc_uid = COALESCE($6, "Items".nfc_uid)
      RETURNING *`,
      [epc, tid, placeId, zoneId, productId, nfcUid]
    );
    return result.rows[0];
  }

  // Check if item exists by EPC
  static async existsByEpc(epc) {
    const result = await pool.query(
      'SELECT EXISTS(SELECT 1 FROM "Items" WHERE item_id = $1) as exists',
      [epc]
    );
    return result.rows[0].exists;
  }

  // Create new item from scan (for mode_b - unregistered EPCs)
  static async createFromScan(epc, placeId, zoneId) {
    const result = await pool.query(
      `INSERT INTO "Items" (item_id, tid, date_creation, date_lastseen, place_last, zone_last, item_product_id, nfc_uid)
       VALUES ($1, '', NOW(), NOW(), $2, $3, '', '')
       ON CONFLICT (item_id) DO UPDATE SET date_lastseen = NOW()
       RETURNING *`,
      [epc, placeId, zoneId]
    );
    return result.rows[0];
  }

  // Get items by place
  static async findByPlace(placeId) {
    const result = await pool.query(
      'SELECT * FROM "Items" WHERE place_last = $1 ORDER BY date_lastseen DESC',
      [placeId]
    );
    return result.rows;
  }

  // Get items by zone
  static async findByZone(zoneId) {
    const result = await pool.query(
      'SELECT * FROM "Items" WHERE zone_last = $1 ORDER BY date_lastseen DESC',
      [zoneId]
    );
    return result.rows;
  }

  // Get items by product
  static async findByProduct(productId) {
    const result = await pool.query(
      'SELECT * FROM "Items" WHERE item_product_id = $1 ORDER BY date_lastseen DESC',
      [productId]
    );
    return result.rows;
  }
}

module.exports = Item;
