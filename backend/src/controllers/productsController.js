const Product = require('../models/Product');

// Ottieni tutti i products
exports.getAllProducts = async (req, res) => {
  try {
    const products = await Product.findAll();
    res.json(products);
  } catch (error) {
    console.error('Error fetching products:', error);
    res.status(500).json({ error: 'Failed to fetch products' });
  }
};

// Ottieni product per ID
exports.getProductById = async (req, res) => {
  const { id } = req.params;

  try {
    const product = await Product.findById(id);

    if (!product) {
      return res.status(404).json({ error: 'Product not found' });
    }

    res.json(product);
  } catch (error) {
    console.error('Error fetching product:', error);
    res.status(500).json({ error: 'Failed to fetch product' });
  }
};

// Ottieni labels dei campi prodotto
exports.getProductLabels = async (req, res) => {
  try {
    const labels = await Product.getLabels();
    res.json(labels);
  } catch (error) {
    console.error('Error fetching product labels:', error);
    res.status(500).json({ error: 'Failed to fetch product labels' });
  }
};

// Crea nuovo product
exports.createProduct = async (req, res) => {
  const { productId, ...fields } = req.body;

  if (!productId) {
    return res.status(400).json({ error: 'productId is required' });
  }

  try {
    const product = await Product.create({ productId, ...fields });
    res.status(201).json(product);
  } catch (error) {
    console.error('Error creating product:', error);
    res.status(500).json({ error: 'Failed to create product', details: error.message });
  }
};

// Aggiorna product
exports.updateProduct = async (req, res) => {
  const { id } = req.params;
  const fields = req.body;

  try {
    const product = await Product.update(id, fields);

    if (!product) {
      return res.status(404).json({ error: 'Product not found' });
    }

    res.json(product);
  } catch (error) {
    console.error('Error updating product:', error);
    res.status(500).json({ error: 'Failed to update product' });
  }
};

// Elimina product
exports.deleteProduct = async (req, res) => {
  const { id } = req.params;

  try {
    const product = await Product.delete(id);

    if (!product) {
      return res.status(404).json({ error: 'Product not found' });
    }

    res.json({ success: true, deleted: product });
  } catch (error) {
    console.error('Error deleting product:', error);
    res.status(500).json({ error: 'Failed to delete product' });
  }
};
