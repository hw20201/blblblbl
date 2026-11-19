#!/usr/bin/env python3
"""Verify the packaged v99 ABI and core pair against the build manifest."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def verify(apk: Path, output: Path) -> None:
    expected = {}
    for line in Path('native/SHA256SUMS').read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        expected[Path(name).name] = digest
    assert set(expected) == {'libclash.so', 'libbridge.so'}, 'Incomplete native pair manifest'
    libraries = {}
    with zipfile.ZipFile(apk) as archive:
        entries = [item.filename for item in archive.infolist() if item.filename.startswith('lib/') and item.filename.endswith('.so')]
        assert entries and all(name.startswith('lib/arm64-v8a/') for name in entries), entries
        for name in entries:
            data = archive.read(name)
            assert data[:6] == b'\x7fELF\x02\x01', f'{name}: expected ELF64 little endian'
            machine = struct.unpack_from('<H', data, 18)[0]
            assert machine == 183, f'{name}: unexpected e_machine {machine}'
            phoff = struct.unpack_from('<Q', data, 32)[0]
            phentsize, phnum = struct.unpack_from('<HH', data, 54)
            alignments = []
            for index in range(phnum):
                offset = phoff + index * phentsize
                if struct.unpack_from('<I', data, offset)[0] == 1:
                    alignments.append(struct.unpack_from('<Q', data, offset + 48)[0])
            assert alignments and all(n >= 16384 for n in alignments), f'{name}: bad LOAD alignment {alignments}'
            digest = hashlib.sha256(data).hexdigest()
            basename = Path(name).name
            if basename in expected:
                assert digest == expected[basename], f'{name}: packaged core does not match manifest'
            libraries[basename] = {'sha256': digest, 'bytes': len(data), 'load_alignments': alignments}
        assert set(expected).issubset(libraries), 'Core pair missing from APK'
    output.mkdir(parents=True, exist_ok=True)
    evidence = {
        'versionCode': 99,
        'versionName': '1.1.0',
        'package': 'com.github.arca.android',
        'abi': 'arm64-v8a',
        'core_base': 'hw20201/mihomo-arca@5d965f69acfaddd6f81bb26feb1cccde102f98db',
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'apk_bytes': apk.stat().st_size,
        'libraries': libraries,
    }
    (output / 'verification.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: verify-apk-v99.py APK OUTPUT_DIRECTORY')
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
