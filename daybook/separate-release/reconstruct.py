from pathlib import Path
import base64, hashlib, lzma, struct, sys, zipfile

root = Path(__file__).resolve().parent
base = Path(sys.argv[1])
def sha(b):
    return hashlib.sha256(b).hexdigest()
assert sha(base.read_bytes()) == "c2691c5f68d323fa0ee05dfc05ed4d1d4147c65a1e4e40a6960bb879ce563a3e", "Unexpected base APK"
with zipfile.ZipFile(base) as z:
    source = b"".join(z.read(n) for n in z.namelist())
assert sha(source) == "5284780440df772a104f8de17d105250a6f26db5a53e96c31468fe009d3708c5"
ops = lzma.decompress(base64.b64decode((root / "bootstrap.delta.b64").read_text(), validate=True))
result = bytearray()
p = 0
while p < len(ops):
    t, n = struct.unpack_from("<BI", ops, p)
    p += 5
    if t == 0:
        assert p + n <= len(ops)
        result += ops[p:p+n]
        p += n
    elif t == 1:
        count, = struct.unpack_from("<I", ops, p)
        p += 4
        assert n + count <= len(source)
        result += source[n:n+count]
    else:
        raise ValueError("Unknown delta operation")
assert len(result) == 202925
assert sha(result) == "8f8dc473fd90d5437e19401f6252002bb7775a20c2320b42adcbe402529c9cf1"
(root / "Dayboox-11.apk").write_bytes(result)
print("Signed APK reconstructed exactly")
