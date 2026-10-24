#!/usr/bin/env python3
"""Offline packaging/privacy checks. Does NOT compile or execute Kotlin/Android."""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
results = []


def record(name, condition, detail):
    results.append({'check': name, 'passed': bool(condition), 'detail': detail})


xml_paths = list((ROOT / 'app/src/main').rglob('*.xml'))
xml_errors = []
for path in xml_paths:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        xml_errors.append(f'{path.relative_to(ROOT)}: {exc}')
record('xml_well_formed', not xml_errors, xml_errors or f'{len(xml_paths)} XML documents parsed')

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
permissions = [item.get(ANDROID + 'name') for item in manifest.findall('uses-permission')]
record('minimal_permissions', set(permissions) == {'android.permission.RECORD_AUDIO', 'android.permission.INTERNET'}, permissions)
services = application.findall('service')
record('authorized_notification_listener_only', len(services) == 1 and
       services[0].get(ANDROID + 'name') == '.automation.AuronNotificationListenerService' and
       services[0].get(ANDROID + 'permission') == 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' and
       services[0].get(ANDROID + 'exported') == 'true' and
       [node.get(ANDROID + 'name') for node in services[0].findall('./intent-filter/action')] ==
       ['android.service.notification.NotificationListenerService'] and not application.findall('receiver'),
       'Only the Android permission-protected notification listener; no hidden receiver or broad package access')
record('backup_disabled', application.get(ANDROID + 'allowBackup') == 'false' and
       application.get(ANDROID + 'fullBackupContent') == 'false', 'Manifest backup flags')
rules = ET.parse(ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').getroot()
required_domains = {'root', 'database', 'sharedpref', 'file', 'external'}
record('backup_and_transfer_excluded', all(
    {item.get('domain') for item in rules.find(section).findall('exclude') if item.get('path') == '.'} >= required_domains
    for section in ['cloud-backup', 'device-transfer']), 'Both extraction sections exclude private and external domains')

missing_targets = []
for activity in application.findall('activity'):
    name = activity.get(ANDROID + 'name')
    qualified = 'br.com.auron' + name if name.startswith('.') else name
    if not (ROOT / 'app/src/main/java' / (qualified.replace('.', '/') + '.kt')).is_file():
        missing_targets.append(qualified)
record('manifest_activity_sources', not missing_targets, missing_targets or 'Declared Activity source exists')

resources = set()
for path in (ROOT / 'app/src/main/res').rglob('*.xml'):
    folder = path.parent.name.split('-')[0]
    if folder == 'values':
        resources.update((item.tag, item.get('name')) for item in ET.parse(path).getroot() if item.get('name'))
    else:
        resources.add((folder, path.stem))
references = re.findall(r'@(?!(?:android):)(\w+)/([\w.]+)',
                        (ROOT / 'app/src/main/AndroidManifest.xml').read_text())
record('manifest_resource_targets', all(ref in resources for ref in references), references)

required_sources = [
    'domain/Models.kt', 'domain/Repositories.kt', 'domain/CommandParser.kt', 'domain/AiProvider.kt',
    'data/AuronDatabase.kt', 'data/AuronDao.kt', 'data/RoomAuronRepository.kt',
    'data/DataStoreSettingsRepository.kt', 'ui/AuronViewModel.kt', 'ui/AuronApp.kt',
    'ui/DashboardScreen.kt', 'ui/ChatScreen.kt', 'ui/ProductivityScreens.kt',
    'ui/SettingsAndModules.kt', 'voice/VoiceController.kt',
    'auth/AuthManager.kt', 'auth/AuthPolicies.kt', 'auth/EncryptedSessionStore.kt', 'auth/SupabaseAuthClient.kt',
    'ai/RemoteAiProvider.kt', 'ai/AiContextBuilder.kt', 'bible/BibleCatalog.kt', 'bible/DailyVerseSelector.kt',
    'ui/AuthScreens.kt', 'ui/BibleReadingSheet.kt', 'ui/AuronRobot.kt',
    'cloud/CloudModels.kt', 'cloud/SupabaseCloudClient.kt', 'ui/CloudBackupScreen.kt',
    'voice/VoiceSelection.kt', 'ui/VoiceSettings.kt', 'ui/OpeningCamera.kt', 'ui/OpeningPreferences.kt',
]
record('source_components_present', all((ROOT / 'app/src/main/java/br/com/auron' / name).is_file()
                                     for name in required_sources), f'{len(required_sources)} required source components')

missing_links = []
for doc in [ROOT / 'README.md', *sorted((ROOT / 'docs').glob('*.md'))]:
    for link in re.findall(r'\]\(([^)]+)\)', doc.read_text()):
        if '://' not in link and not link.startswith('#'):
            path = link.split('#')[0]
            if path and not (doc.parent / path).exists():
                missing_links.append(f'{doc.relative_to(ROOT)} -> {path}')
record('documentation_links', not missing_links, missing_links or 'Local Markdown link targets exist')

summary = {
    'scope': 'Static source/packaging checks only; no Kotlin compilation or Android execution.',
    'checks': results,
    'passed': sum(item['passed'] for item in results),
    'total': len(results),
    'test_sources_not_executed': {
        folder: sum(len(re.findall(r'@Test\b', file.read_text()))
                    for file in (ROOT / f'app/src/{folder}').rglob('*.kt'))
        for folder in ['test', 'androidTest']
    },
}
print(json.dumps(summary, ensure_ascii=False, indent=2))
sys.exit(0 if summary['passed'] == summary['total'] else 1)
