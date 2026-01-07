const pool = require('../db/config');

class Place {
  // Get all places
  static async findAll() {
    const result = await pool.query(
      'SELECT * FROM "Places" ORDER BY place_name'
    );
    return result.rows;
  }

  // Get place by ID
  static async findById(placeId) {
    const result = await pool.query(
      'SELECT * FROM "Places" WHERE place_id = $1',
      [placeId]
    );
    return result.rows[0];
  }

  // Create new place
  static async create(placeData) {
    const { placeId, placeName, placeType } = placeData;
    const result = await pool.query(
      `INSERT INTO "Places" (place_id, place_name, place_type)
       VALUES ($1, $2, $3)
       RETURNING *`,
      [placeId, placeName, placeType]
    );
    return result.rows[0];
  }

  // Update place
  static async update(placeId, placeData) {
    const { placeName, placeType } = placeData;
    const result = await pool.query(
      `UPDATE "Places"
       SET place_name = COALESCE($2, place_name),
           place_type = COALESCE($3, place_type)
       WHERE place_id = $1
       RETURNING *`,
      [placeId, placeName, placeType]
    );
    return result.rows[0];
  }

  // Delete place
  static async delete(placeId) {
    const result = await pool.query(
      'DELETE FROM "Places" WHERE place_id = $1 RETURNING *',
      [placeId]
    );
    return result.rows[0];
  }
}

module.exports = Place;
