from pathlib import Path
import base64, hashlib, lzma, struct, sys, zipfile

root = Path(__file__).resolve().parent
source = Path(sys.argv[1]).read_bytes()
assert hashlib.sha256(source).hexdigest() == "8f8dc473fd90d5437e19401f6252002bb7775a20c2320b42adcbe402529c9cf1", "Unexpected base APK"
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
assert len(result) == 219383
assert hashlib.sha256(result).hexdigest() == "82f2ae469f79288e506d7652b0e1f13aac482c2d467fca51e102d470d8a00950"
(root / "Dayboox-12.apk").write_bytes(result)
print("Signed APK reconstructed exactly")
