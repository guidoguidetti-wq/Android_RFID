const pool = require('../db/config');

class Zone {
  // Get all zones
  static async findAll() {
    const result = await pool.query(
      'SELECT * FROM "Zones" ORDER BY zone_name'
    );
    return result.rows;
  }

  // Get zone by ID
  static async findById(zoneId) {
    const result = await pool.query(
      'SELECT * FROM "Zones" WHERE zone_id = $1',
      [zoneId]
    );
    return result.rows[0];
  }

  // Create new zone
  static async create(zoneData) {
    const { zoneId, zoneName, zoneType } = zoneData;
    const result = await pool.query(
      `INSERT INTO "Zones" (zone_id, zone_name, zone_type)
       VALUES ($1, $2, $3)
       RETURNING *`,
      [zoneId, zoneName, zoneType]
    );
    return result.rows[0];
  }

  // Update zone
  static async update(zoneId, zoneData) {
    const { zoneName, zoneType } = zoneData;
    const result = await pool.query(
      `UPDATE "Zones"
       SET zone_name = COALESCE($2, zone_name),
           zone_type = COALESCE($3, zone_type)
       WHERE zone_id = $1
       RETURNING *`,
      [zoneId, zoneName, zoneType]
    );
    return result.rows[0];
  }

  // Delete zone
  static async delete(zoneId) {
    const result = await pool.query(
      'DELETE FROM "Zones" WHERE zone_id = $1 RETURNING *',
      [zoneId]
    );
    return result.rows[0];
  }
}

module.exports = Zone;
