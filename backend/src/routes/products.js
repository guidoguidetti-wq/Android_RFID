const express = require('express');
const router = express.Router();
const productsController = require('../controllers/productsController');

// GET /api/products - Ottieni tutti i products
router.get('/', productsController.getAllProducts);

// GET /api/products/labels - Ottieni labels campi prodotto
router.get('/labels', productsController.getProductLabels);

// GET /api/products/:id - Ottieni product specifico
router.get('/:id', productsController.getProductById);

// POST /api/products - Crea nuovo product
router.post('/', productsController.createProduct);

// PUT /api/products/:id - Aggiorna product
router.put('/:id', productsController.updateProduct);

// DELETE /api/products/:id - Elimina product
router.delete('/:id', productsController.deleteProduct);

module.exports = router;
