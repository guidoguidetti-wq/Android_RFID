const Item = require('../models/Item');

// Ottieni tutti gli items
exports.getAllItems = async (req, res) => {
  const limit = parseInt(req.query.limit) || 100;
  const offset = parseInt(req.query.offset) || 0;

  try {
    const items = await Item.findAll(limit, offset);
    res.json(items);
  } catch (error) {
    console.error('Error fetching items:', error);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
};

// Ottieni item per EPC
exports.getItemByEpc = async (req, res) => {
  const { epc } = req.params;

  try {
    const item = await Item.findByEpc(epc);

    if (!item) {
      return res.status(404).json({ error: 'Item not found' });
    }

    res.json(item);
  } catch (error) {
    console.error('Error fetching item:', error);
    res.status(500).json({ error: 'Failed to fetch item' });
  }
};

// Ottieni items per place
exports.getItemsByPlace = async (req, res) => {
  const { placeId } = req.params;

  try {
    const items = await Item.findByPlace(placeId);
    res.json(items);
  } catch (error) {
    console.error('Error fetching items by place:', error);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
};

// Ottieni items per zone
exports.getItemsByZone = async (req, res) => {
  const { zoneId } = req.params;

  try {
    const items = await Item.findByZone(zoneId);
    res.json(items);
  } catch (error) {
    console.error('Error fetching items by zone:', error);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
};

// Ottieni items per product
exports.getItemsByProduct = async (req, res) => {
  const { productId } = req.params;

  try {
    const items = await Item.findByProduct(productId);
    res.json(items);
  } catch (error) {
    console.error('Error fetching items by product:', error);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
};

// Crea o aggiorna item
exports.upsertItem = async (req, res) => {
  const {
    epc,
    tid = null,
    placeId = null,
    zoneId = null,
    productId = null,
    nfcUid = null
  } = req.body;

  if (!epc) {
    return res.status(400).json({ error: 'EPC is required' });
  }

  try {
    const item = await Item.upsert({
      epc,
      tid,
      placeId,
      zoneId,
      productId,
      nfcUid
    });

    res.json(item);
  } catch (error) {
    console.error('Error upserting item:', error);
    res.status(500).json({ error: 'Failed to upsert item', details: error.message });
  }
};
