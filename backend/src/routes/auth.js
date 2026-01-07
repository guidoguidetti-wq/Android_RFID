const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');

/**
 * Auth Routes
 * Base path: /api/auth
 */

// POST /api/auth/login - Autentica utente
router.post('/login', authController.login);

// POST /api/auth/register - Registra nuovo utente
router.post('/register', authController.register);

// GET /api/auth/validate/:username - Verifica se username esiste
router.get('/validate/:username', authController.validateUser);

// GET /api/auth/users - Ottieni tutti gli utenti attivi
router.get('/users', authController.getAllUsers);

// PUT /api/auth/users/:userId - Aggiorna dati utente
router.put('/users/:userId', authController.updateUser);

// DELETE /api/auth/users/:userId - Disattiva utente
router.delete('/users/:userId', authController.deactivateUser);

module.exports = router;
