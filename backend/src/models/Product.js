const pool = require('../db/config');

class Product {
  // Get all products
  static async findAll() {
    const result = await pool.query(
      'SELECT * FROM "Products" ORDER BY product_id'
    );
    return result.rows;
  }

  // Get product by ID
  static async findById(productId) {
    const result = await pool.query(
      'SELECT * FROM "Products" WHERE product_id = $1',
      [productId]
    );
    return result.rows[0];
  }

  // Get product labels (field metadata)
  static async getLabels() {
    const result = await pool.query(
      'SELECT * FROM "Products_labels" ORDER BY pr_fld'
    );
    return result.rows;
  }

  // Create new product
  static async create(productData) {
    const { productId, ...fields } = productData;

    // Build dynamic query based on provided fields
    const fieldNames = Object.keys(fields);
    const values = [productId, ...Object.values(fields)];
    const placeholders = fieldNames.map((_, i) => `$${i + 2}`).join(', ');

    const result = await pool.query(
      `INSERT INTO "Products" (product_id${fieldNames.length > 0 ? ', ' + fieldNames.join(', ') : ''})
       VALUES ($1${fieldNames.length > 0 ? ', ' + placeholders : ''})
       RETURNING *`,
      values
    );
    return result.rows[0];
  }

  // Update product
  static async update(productId, productData) {
    const fieldNames = Object.keys(productData);
    if (fieldNames.length === 0) return null;

    const setClause = fieldNames.map((field, i) => `${field} = $${i + 2}`).join(', ');
    const values = [productId, ...Object.values(productData)];

    const result = await pool.query(
      `UPDATE "Products" SET ${setClause} WHERE product_id = $1 RETURNING *`,
      values
    );
    return result.rows[0];
  }

  // Delete product
  static async delete(productId) {
    const result = await pool.query(
      'DELETE FROM "Products" WHERE product_id = $1 RETURNING *',
      [productId]
    );
    return result.rows[0];
  }
}

module.exports = Product;
