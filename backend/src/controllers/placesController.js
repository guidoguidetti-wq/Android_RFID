const Place = require('../models/Place');

// Ottieni tutti i places
exports.getAllPlaces = async (req, res) => {
  try {
    const places = await Place.findAll();
    res.json(places);
  } catch (error) {
    console.error('Error fetching places:', error);
    res.status(500).json({ error: 'Failed to fetch places' });
  }
};

// Ottieni place per ID
exports.getPlaceById = async (req, res) => {
  const { id } = req.params;

  try {
    const place = await Place.findById(id);

    if (!place) {
      return res.status(404).json({ error: 'Place not found' });
    }

    res.json(place);
  } catch (error) {
    console.error('Error fetching place:', error);
    res.status(500).json({ error: 'Failed to fetch place' });
  }
};

// Crea nuovo place
exports.createPlace = async (req, res) => {
  const { placeId, placeName, placeType } = req.body;

  if (!placeId || !placeName) {
    return res.status(400).json({ error: 'placeId and placeName are required' });
  }

  try {
    const place = await Place.create({ placeId, placeName, placeType });
    res.status(201).json(place);
  } catch (error) {
    console.error('Error creating place:', error);
    res.status(500).json({ error: 'Failed to create place', details: error.message });
  }
};

// Aggiorna place
exports.updatePlace = async (req, res) => {
  const { id } = req.params;
  const { placeName, placeType } = req.body;

  try {
    const place = await Place.update(id, { placeName, placeType });

    if (!place) {
      return res.status(404).json({ error: 'Place not found' });
    }

    res.json(place);
  } catch (error) {
    console.error('Error updating place:', error);
    res.status(500).json({ error: 'Failed to update place' });
  }
};

// Elimina place
exports.deletePlace = async (req, res) => {
  const { id } = req.params;

  try {
    const place = await Place.delete(id);

    if (!place) {
      return res.status(404).json({ error: 'Place not found' });
    }

    res.json({ success: true, deleted: place });
  } catch (error) {
    console.error('Error deleting place:', error);
    res.status(500).json({ error: 'Failed to delete place' });
  }
};
