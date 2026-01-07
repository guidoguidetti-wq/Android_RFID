const { Pool } = require('pg');

const pool = new Pool({
  host: '57.129.5.234',
  port: 5432,
  database: 'rfid_db',
  user: 'rfidmanager',
  password: 'iniAD16Z77oS',
});

async function inspectDatabase() {
  try {
    console.log('Connecting to database...\n');

    // List all tables
    const tablesResult = await pool.query(`
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = 'public'
      ORDER BY table_name;
    `);

    console.log('=== TABLES ===');
    tablesResult.rows.forEach(row => console.log(`- ${row.table_name}`));
    console.log('\n');

    // Get schema for each table
    for (const table of tablesResult.rows) {
      const tableName = table.table_name;

      console.log(`=== TABLE: ${tableName} ===`);

      const schemaResult = await pool.query(`
        SELECT
          column_name,
          data_type,
          character_maximum_length,
          is_nullable,
          column_default
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = $1
        ORDER BY ordinal_position;
      `, [tableName]);

      schemaResult.rows.forEach(col => {
        const type = col.character_maximum_length
          ? `${col.data_type}(${col.character_maximum_length})`
          : col.data_type;
        const nullable = col.is_nullable === 'YES' ? 'NULL' : 'NOT NULL';
        const defaultVal = col.column_default ? ` DEFAULT ${col.column_default}` : '';
        console.log(`  ${col.column_name}: ${type} ${nullable}${defaultVal}`);
      });

      // Get primary keys
      const pkResult = await pool.query(`
        SELECT a.attname
        FROM pg_index i
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
        WHERE i.indrelid = '"${tableName}"'::regclass AND i.indisprimary;
      `);

      if (pkResult.rows.length > 0) {
        console.log(`  PRIMARY KEY: ${pkResult.rows.map(r => r.attname).join(', ')}`);
      }

      // Get foreign keys
      const fkResult = await pool.query(`
        SELECT
          tc.constraint_name,
          kcu.column_name,
          ccu.table_name AS foreign_table_name,
          ccu.column_name AS foreign_column_name
        FROM information_schema.table_constraints AS tc
        JOIN information_schema.key_column_usage AS kcu
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        JOIN information_schema.constraint_column_usage AS ccu
          ON ccu.constraint_name = tc.constraint_name
          AND ccu.table_schema = tc.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = $1;
      `, [tableName]);

      if (fkResult.rows.length > 0) {
        fkResult.rows.forEach(fk => {
          console.log(`  FOREIGN KEY: ${fk.column_name} -> ${fk.foreign_table_name}(${fk.foreign_column_name})`);
        });
      }

      // Sample data count
      const countResult = await pool.query(`SELECT COUNT(*) FROM "${tableName}"`);
      console.log(`  ROWS: ${countResult.rows[0].count}`);

      console.log('\n');
    }

  } catch (error) {
    console.error('Error:', error.message);
  } finally {
    await pool.end();
  }
}

inspectDatabase();
