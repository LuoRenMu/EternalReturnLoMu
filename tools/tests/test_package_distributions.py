from pathlib import Path
import hashlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_distributions import package


class PackageDistributionsTest(unittest.TestCase):
    def fixture(self, root):
        (root / 'gradle.properties').write_text('lomuCoreVersion=3.0.4\n', encoding='utf-8')
        for file in ('README.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'nutdraw/src/main/resources/fonts/OFL.txt',
            'core/src/main/resources/static/admin/vendor/LICENSE.alpine.md', 'core/src/main/resources/static/admin/vendor/LICENSE.htmx.txt'):
            path = root / file; path.parent.mkdir(parents=True, exist_ok=True); path.write_text('fixture', encoding='utf-8')
        for adapter in ('qgbot', 'onebot'):
            path = root / adapter / 'build/libs' / (adapter + '-3.0.4.jar'); path.parent.mkdir(parents=True)
            with zipfile.ZipFile(path, 'w') as jar:
                for entry in ('META-INF/LICENSE-AGPL-3.0.txt', 'META-INF/THIRD_PARTY_NOTICES.md'): jar.writestr(entry, 'fixture')
        plugins = root / 'build/command-plugins'; plugins.mkdir(parents=True)
        with zipfile.ZipFile(plugins / 'fixture-1.0.0.jar', 'w') as jar: jar.writestr('META-INF/MANIFEST.MF', 'fixture')

    def test_explicit_bundles_include_licenses_and_match_checksums_without_private_state(self):
        with tempfile.TemporaryDirectory(prefix='lomu-package-') as directory:
            root = Path(directory); self.fixture(root)
            (root / 'config.json').write_text('PRIVATE_CONFIG')
            (root / 'data').mkdir(); (root / 'data/private.db').write_text('PRIVATE_DATABASE')
            archives = package(root)
            self.assertEqual(3, len(archives))
            sums = (root / 'build/release/SHA256SUMS').read_text().splitlines()
            for archive, line in zip(archives, sums):
                self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest() + '  ' + archive.name, line)
                with zipfile.ZipFile(archive) as bundle:
                    names = bundle.namelist()
                    self.assertTrue(any(n.endswith('/LICENSE') for n in names))
                    self.assertTrue(any('/licenses/Alpine-MIT.md' in n for n in names))
                    self.assertTrue(any('/plugins/fixture-1.0.0.jar' in n for n in names))
                    self.assertFalse(any('config.json' in n or '/data/' in n for n in names))

    def test_missing_license_aborts_before_producing_an_archive(self):
        with tempfile.TemporaryDirectory(prefix='lomu-package-') as directory:
            root = Path(directory); self.fixture(root)
            (root / 'LICENSE').unlink()
            with self.assertRaises(AssertionError): package(root)
            self.assertFalse((root / 'build/release').exists())


if __name__ == '__main__': unittest.main()
