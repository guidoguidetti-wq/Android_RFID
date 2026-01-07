const Zone = require('../models/Zone');

// Ottieni tutte le zones
exports.getAllZones = async (req, res) => {
  try {
    const zones = await Zone.findAll();
    res.json(zones);
  } catch (error) {
    console.error('Error fetching zones:', error);
    res.status(500).json({ error: 'Failed to fetch zones' });
  }
};

// Ottieni zone per ID
exports.getZoneById = async (req, res) => {
  const { id } = req.params;

  try {
    const zone = await Zone.findById(id);

    if (!zone) {
      return res.status(404).json({ error: 'Zone not found' });
    }

    res.json(zone);
  } catch (error) {
    console.error('Error fetching zone:', error);
    res.status(500).json({ error: 'Failed to fetch zone' });
  }
};

// Crea nuova zone
exports.createZone = async (req, res) => {
  const { zoneId, zoneName, zoneType } = req.body;

  if (!zoneId || !zoneName) {
    return res.status(400).json({ error: 'zoneId and zoneName are required' });
  }

  try {
    const zone = await Zone.create({ zoneId, zoneName, zoneType });
    res.status(201).json(zone);
  } catch (error) {
    console.error('Error creating zone:', error);
    res.status(500).json({ error: 'Failed to create zone', details: error.message });
  }
};

// Aggiorna zone
exports.updateZone = async (req, res) => {
  const { id } = req.params;
  const { zoneName, zoneType } = req.body;

  try {
    const zone = await Zone.update(id, { zoneName, zoneType });

    if (!zone) {
      return res.status(404).json({ error: 'Zone not found' });
    }

    res.json(zone);
  } catch (error) {
    console.error('Error updating zone:', error);
    res.status(500).json({ error: 'Failed to update zone' });
  }
};

// Elimina zone
exports.deleteZone = async (req, res) => {
  const { id } = req.params;

  try {
    const zone = await Zone.delete(id);

    if (!zone) {
      return res.status(404).json({ error: 'Zone not found' });
    }

    res.json({ success: true, deleted: zone });
  } catch (error) {
    console.error('Error deleting zone:', error);
    res.status(500).json({ error: 'Failed to delete zone' });
  }
};
