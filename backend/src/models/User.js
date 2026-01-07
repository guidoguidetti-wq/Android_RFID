const pool = require('../db/config');

class User {
  /**
   * Trova un utente per username
   * @param {string} username - User name
   * @returns {Promise<Object|null>} User object o null se non trovato
   */
  static async findByUsername(username) {
    const result = await pool.query(
      'SELECT * FROM users WHERE usr_name = $1',
      [username]
    );
    return result.rows[0] || null;
  }

  /**
   * Autentica un utente con username e password
   * @param {string} username - User name
   * @param {string} password - Password (plain text)
   * @returns {Promise<Object|null>} User object sanitizzato o null se credenziali non valide
   */
  static async authenticate(username, password) {
    // Query with JOIN to get place details
    const result = await pool.query(
      `SELECT u.*, p.place_id, p.place_name, p.place_type
       FROM "users" u
       LEFT JOIN "Places" p ON u.usr_def_place = p.place_id
       WHERE u.usr_name = $1`,
      [username]
    );

    const user = result.rows[0];

    if (!user) {
      console.log(`Authentication failed: User '${username}' not found`);
      return null;
    }

    // Password plain text comparison
    if (user.usr_pwd !== password) {
      console.log(`Authentication failed: Invalid password for user '${username}'`);
      return null;
    }

    console.log(`User '${username}' authenticated successfully`);

    // Return sanitized user object (without password) with place details
    return {
      user_id: user.usr_name,
      user_name: user.usr_name,
      user_role: 'operator',
      usr_def_place: user.usr_def_place ? String(user.usr_def_place) : null,
      place_details: {
        place_id: user.place_id,
        place_name: user.place_name,
        place_type: user.place_type
      }
    };
  }

  /**
   * Crea un nuovo utente
   * @param {Object} userData - Dati utente
   * @param {string} userData.userId - User ID
   * @param {string} userData.password - Password
   * @param {string} userData.name - Nome visualizzato
   * @param {string} userData.email - Email
   * @param {string} userData.placeId - Place ID predefinito
   * @returns {Promise<Object>} User creato
   */
  static async create(userData) {
    const { userId, password, name, email, placeId } = userData;
    const result = await pool.query(
      `INSERT INTO "Users" (user_id, user_password, user_name, user_email, usr_def_place)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [userId, password, name, email, placeId]
    );
    console.log(`User '${userId}' created successfully`);
    return result.rows[0];
  }

  /**
   * Aggiorna i dati di un utente
   * @param {string} userId - User ID
   * @param {Object} updates - Campi da aggiornare
   * @returns {Promise<Object>} User aggiornato
   */
  static async update(userId, updates) {
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

    values.push(userId);
    const result = await pool.query(
      `UPDATE "Users" SET ${fields.join(', ')} WHERE user_id = $${paramIndex} RETURNING *`,
      values
    );

    return result.rows[0];
  }

  /**
   * Disattiva un utente
   * @param {string} userId - User ID
   * @returns {Promise<Object>} User disattivato
   */
  static async deactivate(userId) {
    const result = await pool.query(
      'UPDATE "Users" SET user_active = false WHERE user_id = $1 RETURNING *',
      [userId]
    );
    console.log(`User '${userId}' deactivated`);
    return result.rows[0];
  }

  /**
   * Ottieni tutti gli utenti attivi
   * @returns {Promise<Array>} Lista utenti
   */
  static async getAllActive() {
    const result = await pool.query(
      'SELECT user_id, user_name, user_email, user_role, usr_def_place, date_created, date_lastlogin FROM "Users" WHERE user_active = true ORDER BY user_name'
    );
    return result.rows;
  }
}

module.exports = User;
