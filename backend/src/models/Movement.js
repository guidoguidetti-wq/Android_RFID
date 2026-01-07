const pool = require('../db/config');

class Movement {
  // Create new movement
  static async create(movementData) {
    const {
      epc,
      placeId = null,
      zoneId = null,
      unexpected = false,
      readsCount = 1,
      rssiAvg = null,
      user = null,
      reader = null,
      readerPower = null,
      notes = null,
      reference = null,
      antennaPower1 = null,
      antennaPower2 = null,
      antennaPower3 = null,
      antennaPower4 = null,
      antenna = null
    } = movementData;

    const result = await pool.query(
      `INSERT INTO "Movements" (
        mov_epc, mov_dest_place, mov_dest_zone, mov_timestamp,
        mov_unexpected, mov_readscount, mov_rssiavg, mov_user,
        mov_reader, mov_readerpw, mov_notes, mov_ref,
        mov_antpw1, mov_antpw2, mov_antpw3, mov_antpw4, mov_antenna
      )
      VALUES ($1, $2, $3, NOW(), $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
      RETURNING *`,
      [
        epc, placeId, zoneId, unexpected, readsCount, rssiAvg, user,
        reader, readerPower, notes, reference,
        antennaPower1, antennaPower2, antennaPower3, antennaPower4, antenna
      ]
    );
    return result.rows[0];
  }

  // Get movements by EPC
  static async findByEpc(epc, limit = 100) {
    const result = await pool.query(
      `SELECT m.*,
        p.place_name,
        z.zone_name
       FROM "Movements" m
       LEFT JOIN "Places" p ON m.mov_dest_place = p.place_id
       LEFT JOIN "Zones" z ON m.mov_dest_zone = z.zone_id
       WHERE m.mov_epc = $1
       ORDER BY m.mov_timestamp DESC
       LIMIT $2`,
      [epc, limit]
    );
    return result.rows;
  }

  // Get recent movements
  static async findRecent(limit = 100, offset = 0) {
    const result = await pool.query(
      `SELECT m.*,
        i.item_product_id,
        p.place_name,
        z.zone_name
       FROM "Movements" m
       LEFT JOIN "Items" i ON m.mov_epc = i.item_id
       LEFT JOIN "Places" p ON m.mov_dest_place = p.place_id
       LEFT JOIN "Zones" z ON m.mov_dest_zone = z.zone_id
       ORDER BY m.mov_timestamp DESC
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    return result.rows;
  }

  // Get movements by place
  static async findByPlace(placeId, limit = 100) {
    const result = await pool.query(
      `SELECT * FROM "Movements"
       WHERE mov_dest_place = $1
       ORDER BY mov_timestamp DESC
       LIMIT $2`,
      [placeId, limit]
    );
    return result.rows;
  }

  // Get movements by zone
  static async findByZone(zoneId, limit = 100) {
    const result = await pool.query(
      `SELECT * FROM "Movements"
       WHERE mov_dest_zone = $1
       ORDER BY mov_timestamp DESC
       LIMIT $2`,
      [zoneId, limit]
    );
    return result.rows;
  }

  // Get movements by time range
  static async findByDateRange(startDate, endDate) {
    const result = await pool.query(
      `SELECT m.*,
        p.place_name,
        z.zone_name
       FROM "Movements" m
       LEFT JOIN "Places" p ON m.mov_dest_place = p.place_id
       LEFT JOIN "Zones" z ON m.mov_dest_zone = z.zone_id
       WHERE m.mov_timestamp BETWEEN $1 AND $2
       ORDER BY m.mov_timestamp DESC`,
      [startDate, endDate]
    );
    return result.rows;
  }

  // Get unexpected movements
  static async findUnexpected(limit = 50) {
    const result = await pool.query(
      `SELECT m.*,
        i.item_product_id,
        p.place_name,
        z.zone_name
       FROM "Movements" m
       LEFT JOIN "Items" i ON m.mov_epc = i.item_id
       LEFT JOIN "Places" p ON m.mov_dest_place = p.place_id
       LEFT JOIN "Zones" z ON m.mov_dest_zone = z.zone_id
       WHERE m.mov_unexpected = true
       ORDER BY m.mov_timestamp DESC
       LIMIT $1`,
      [limit]
    );
    return result.rows;
  }
}

module.exports = Movement;
