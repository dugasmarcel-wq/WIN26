from pathlib import Path
import base64, hashlib, lzma, struct, sys, zipfile

root = Path(__file__).resolve().parent
source = Path(sys.argv[1]).read_bytes()
assert hashlib.sha256(source).hexdigest() == "3ec74d571f742ce809a7594acd0b68a6aeb466c056a3bb725a07c2b397fe8475", "Unexpected base APK"
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
assert len(result) == 235767
assert hashlib.sha256(result).hexdigest() == "d42f3f48800ac75edfdd433dce8f06b778cb971782cf564fe820b82b7cd9faf0"
(root / "Dayboox-14.apk").write_bytes(result)
print("Signed APK reconstructed exactly")
