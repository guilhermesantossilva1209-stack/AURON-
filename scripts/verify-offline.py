#!/usr/bin/env python3
"""Exercise the literal migration SQL and validate bundled assets, without Android.

This does not execute Kotlin, Room's schema validator, Android Keystore, or Compose.
The Android migration test remains necessary on a device/emulator.
"""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]


class OfflineValidation(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')

    def tearDown(self):
        self.db.close()

    def migrate_fixture(self):
        fixture = (ROOT / 'app/src/androidTest/java/br/com/auron/data/AuronMigrationTest.kt').read_text()
        statements = re.findall(r'old\.execSQL\("([^"\n]+)"\)', fixture)
        self.assertGreaterEqual(len(statements), 14)
        for sql in statements:
            self.db.execute(sql)
        before = {
            table: self.db.execute(f'SELECT * FROM {table}').fetchall()
            for table in ['tasks', 'routine_items', 'routine_completions', 'chat_messages', 'database_metadata']
        }
        source = (ROOT / 'app/src/main/java/br/com/auron/data/AuronDatabase.kt').read_text()
        migration = re.findall(r'db\.execSQL\("([^"\n]+)"\)', source)
        self.assertEqual(4, len(migration), 'Review this check when changing migration SQL')
        with self.db:
            for sql in migration:
                self.db.execute(sql)
        return before

    def test_actual_migration_sql_preserves_all_legacy_rows(self):
        before = self.migrate_fixture()
        for table, rows in before.items():
            result = self.db.execute(f'SELECT * FROM {table}').fetchall()
            expected = [row + (None,) for row in rows] if table == 'routine_items' else rows
            self.assertEqual(expected, result, table)
        self.assertEqual([('ok',)], self.db.execute('PRAGMA integrity_check').fetchall())

    def test_new_tables_keep_one_reading_per_day_and_store_ciphertext(self):
        self.migrate_fixture()
        self.db.execute('INSERT INTO daily_readings VALUES (?, ?)', ('2026-10-18', '["one"]'))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO daily_readings VALUES (?, ?)', ('2026-10-18', '["changed"]'))
        self.assertEqual('["one"]', self.db.execute('SELECT verseIdsJson FROM daily_readings').fetchone()[0])
        columns = [row[1] for row in self.db.execute('PRAGMA table_info(memories)')]
        self.assertEqual(['id', 'ciphertext', 'iv', 'updatedAt'], columns)
        self.assertNotIn('content', columns)

    def test_bible_asset_has_complete_unique_groups_and_attribution(self):
        asset = json.loads((ROOT / 'app/src/main/assets/bible_daily.json').read_text())
        verses = asset['verses']
        self.assertEqual(35, len(verses))
        self.assertEqual(35, len({v['id'] for v in verses}))
        self.assertEqual(35, len({v['reference'] for v in verses}))
        for verse in verses:
            for field in ['id', 'reference', 'text', 'reflection']:
                self.assertTrue(verse[field].strip())
            self.assertRegex(verse['reference'], r'^(Salmos 23|Provérbios 3|Filipenses 4):\d+$')
        self.assertEqual('CC BY 4.0', asset['license'])
        self.assertIn('Diego Santos', asset['copyright'])
        self.assertEqual('https://ebible.org/porbr2018/copyright.htm', asset['source'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
