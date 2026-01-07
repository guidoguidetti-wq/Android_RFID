const User = require('../models/User');

/**
 * POST /api/auth/login
 * Autentica un utente con username e password
 */
exports.login = async (req, res) => {
  try {
    const { username, password } = req.body;

    // Validazione input
    if (!username || !password) {
      return res.status(400).json({
        error: 'Username and password required'
      });
    }

    console.log(`Login attempt for user: ${username}`);

    // Autentica
    const user = await User.authenticate(username, password);

    if (!user) {
      return res.status(401).json({
        error: 'Invalid credentials'
      });
    }

    // Success response
    res.json({
      success: true,
      user: user
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({
      error: 'Login failed',
      details: error.message
    });
  }
};

/**
 * POST /api/auth/register
 * Registra un nuovo utente
 */
exports.register = async (req, res) => {
  try {
    const { userId, password, name, email, placeId } = req.body;

    // Validazione input
    if (!userId || !password || !placeId) {
      return res.status(400).json({
        error: 'userId, password, and placeId are required'
      });
    }

    console.log(`Registration attempt for user: ${userId}`);

    // Verifica che l'utente non esista già
    const existingUser = await User.findByUsername(userId);
    if (existingUser) {
      return res.status(409).json({
        error: 'User already exists'
      });
    }

    // Crea utente
    const user = await User.create({ userId, password, name, email, placeId });

    res.status(201).json({
      success: true,
      user: {
        user_id: user.user_id,
        user_name: user.user_name,
        user_role: user.user_role,
        usr_def_place: user.usr_def_place
      }
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({
      error: 'Registration failed',
      details: error.message
    });
  }
};

/**
 * GET /api/auth/validate/:username
 * Verifica se un username esiste
 */
exports.validateUser = async (req, res) => {
  try {
    const { username } = req.params;

    const user = await User.findByUsername(username);

    res.json({
      exists: !!user,
      user_id: user ? user.user_id : null
    });
  } catch (error) {
    console.error('Validation error:', error);
    res.status(500).json({
      error: 'Validation failed'
    });
  }
};

/**
 * GET /api/auth/users
 * Ottieni tutti gli utenti attivi (admin only in produzione)
 */
exports.getAllUsers = async (req, res) => {
  try {
    const users = await User.getAllActive();
    res.json(users);
  } catch (error) {
    console.error('Error fetching users:', error);
    res.status(500).json({
      error: 'Failed to fetch users'
    });
  }
};

/**
 * PUT /api/auth/users/:userId
 * Aggiorna dati utente
 */
exports.updateUser = async (req, res) => {
  try {
    const { userId } = req.params;
    const updates = req.body;

    // Non permettere aggiornamento password tramite questo endpoint
    delete updates.user_password;

    const user = await User.update(userId, updates);

    if (!user) {
      return res.status(404).json({
        error: 'User not found'
      });
    }

    res.json({
      success: true,
      user: {
        user_id: user.user_id,
        user_name: user.user_name,
        user_role: user.user_role,
        usr_def_place: user.usr_def_place
      }
    });
  } catch (error) {
    console.error('Update user error:', error);
    res.status(500).json({
      error: 'Failed to update user',
      details: error.message
    });
  }
};

/**
 * DELETE /api/auth/users/:userId
 * Disattiva un utente
 */
exports.deactivateUser = async (req, res) => {
  try {
    const { userId } = req.params;

    const user = await User.deactivate(userId);

    if (!user) {
      return res.status(404).json({
        error: 'User not found'
      });
    }

    res.json({
      success: true,
      message: `User '${userId}' deactivated`
    });
  } catch (error) {
    console.error('Deactivate user error:', error);
    res.status(500).json({
      error: 'Failed to deactivate user',
      details: error.message
    });
  }
};
