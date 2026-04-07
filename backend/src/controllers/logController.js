const pool = require('../db/config');
const crypto = require('crypto');

/**
 * POST /api/log
 * Inserisce un record di log nella tabella log.
 * Body: { userId, logText }
 */
exports.createLog = async (req, res) => {
  try {
    const { userId, logText } = req.body;
    if (!logText) return res.status(400).json({ error: 'logText required' });

    const logId       = crypto.randomUUID();
    const logDatetime = new Date().toISOString();
    const logUser     = userId || 'unknown';

    await pool.query(
      `INSERT INTO log (log_id, log_datetime, log_user, log_text) VALUES ($1, $2, $3, $4)`,
      [logId, logDatetime, logUser, logText]
    );

    console.log(`[LOG] user=${logUser} → ${logText}`);
    res.json({ success: true, log_id: logId });
  } catch (error) {
    // Non ritorniamo 500: il logging non deve bloccare l'app client
    console.error('Error writing log:', error);
    res.json({ success: false, error: error.message });
  }
};
