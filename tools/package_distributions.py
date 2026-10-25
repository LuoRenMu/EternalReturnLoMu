"""Build explicit, secret-free release bundles from staged application/plugin JARs."""
from pathlib import Path
import hashlib
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def package(root=ROOT):
    properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
    version = properties['lomuCoreVersion']
    assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'
    plugins = sorted((root / 'build/command-plugins').glob('*.jar'))
    assert plugins, 'No staged plugins'
    common = [(root / name, name) for name in ('README.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md')]
    common += [(root / source, 'licenses/' + name) for source, name in (
        ('nutdraw/src/main/resources/fonts/OFL.txt', 'Noto-OFL.txt'),
        ('core/src/main/resources/static/admin/vendor/LICENSE.alpine.md', 'Alpine-MIT.md'),
        ('core/src/main/resources/static/admin/vendor/LICENSE.htmx.txt', 'htmx-0BSD.txt'),
    )]
    for source, _ in common: assert source.is_file(), 'Missing license/document: ' + str(source)
    output = root / 'build/release'
    output.mkdir(parents=True, exist_ok=True)
    archives = []
    for kind in ('qgbot', 'onebot', 'plugins'):
        prefix = kind + '-' + version
        entries = list(common) + [(p, 'plugins/' + p.name) for p in plugins]
        if kind != 'plugins':
            main = root / kind / 'build/libs' / (prefix + '.jar')
            assert main.is_file(), 'Missing main JAR'
            with zipfile.ZipFile(main) as jar:
                assert 'META-INF/LICENSE-AGPL-3.0.txt' in jar.namelist(), 'Missing embedded project license'
                assert 'META-INF/THIRD_PARTY_NOTICES.md' in jar.namelist(), 'Missing embedded third-party notice'
            entries.append((main, main.name))
        archive = output / (prefix + '.zip')
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
            for source, name in entries: bundle.write(source, prefix + '/' + name)
        with zipfile.ZipFile(archive) as bundle:
            assert bundle.testzip() is None
            assert not any(any(part in ('config.json', 'data', 'logs', '.cache') for part in Path(n).parts) for n in bundle.namelist())
        archives.append(archive)
        print('PACKAGED', archive.name, 'bytes=' + str(archive.stat().st_size))
    checksums = ''.join(hashlib.sha256(file.read_bytes()).hexdigest() + '  ' + file.name + '\n' for file in archives)
    (output / 'SHA256SUMS').write_text(checksums, encoding='ascii')
    return archives


if __name__ == '__main__': package()
