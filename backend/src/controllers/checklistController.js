const Checklist = require('../models/Checklist');

/**
 * GET /api/checklist
 * Ottieni checklist con filtro opzionale per tipo
 */
exports.getAll = async (req, res) => {
  try {
    const { type } = req.query;

    let checklists;
    if (type) {
      console.log(`Fetching checklists of type: ${type}`);
      checklists = await Checklist.findByType(type);
    } else {
      checklists = await Checklist.getAll();
    }

    res.json(checklists);
  } catch (error) {
    console.error('Error fetching checklists:', error);
    res.status(500).json({
      error: 'Failed to fetch checklists',
      details: error.message
    });
  }
};

/**
 * GET /api/checklist/:id
 * Ottieni una checklist specifica
 */
exports.getById = async (req, res) => {
  try {
    const { id } = req.params;

    const checklist = await Checklist.findById(id);

    if (!checklist) {
      return res.status(404).json({
        error: 'Checklist not found'
      });
    }

    res.json(checklist);
  } catch (error) {
    console.error('Error fetching checklist:', error);
    res.status(500).json({
      error: 'Failed to fetch checklist',
      details: error.message
    });
  }
};

/**
 * POST /api/checklist
 * Crea una nuova checklist
 */
exports.create = async (req, res) => {
  try {
    const { code, type, place, zone, notes } = req.body;

    if (!code || !type) {
      return res.status(400).json({
        error: 'code and type are required'
      });
    }

    const checklist = await Checklist.create({ code, type, place, zone, notes });

    res.status(201).json({
      success: true,
      checklist
    });
  } catch (error) {
    console.error('Error creating checklist:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to create checklist',
      details: error.message
    });
  }
};
