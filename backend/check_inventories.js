const { Client } = require('pg');

async function checkInventories() {
  const client = new Client({
    host: '57.129.5.234',
    port: 5432,
    database: 'rfid_db',
    user: 'rfidmanager',
    password: 'iniAD16Z77oS'
  });

  try {
    await client.connect();
    console.log('Checking inventory_items schema...\n');

    const result = await client.query(`
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_name = 'inventory_items'
      ORDER BY ordinal_position
    `);
    console.log('Columns:', result.rows);

    await client.end();
  } catch (error) {
    console.error('Error:', error.message);
    await client.end();
  }
}

checkInventories();
