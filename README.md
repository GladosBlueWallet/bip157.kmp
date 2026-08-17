# bip157

Kotlin Multiplatform [BIP-157](https://github.com/bitcoin/bips/blob/master/bip-0157.mediawiki) client-side compact-filter protocol. Wire codec for the six P2P messages, filter-header derive/verify helpers, and a sans-I/O sync state machine.

This is a port of the TypeScript [`bip157`](https://github.com/Overtorment/bip157) library. The public API keeps the same names and behavior so a wallet similar to Blueberry can call it the same way. Primary targets are **Android** and **iOS**; JVM and linuxX64 are also built.

This package implements **client-side filter sync** — not GCS construction, not sockets. Filter-hash / filter-header math lives in-tree (`filterHash`, `filterHeader`). Compose with a BIP-158 GCS library when you need filter build/match, and with BIP-324 (or your own transport) for encrypted P2P framing.

## Example

```kotlin
import io.bluewallet.bip157.bytesToHex
import io.bluewallet.bip157.createFilterSync
import io.bluewallet.bip157.decodeGetCFilters
import io.bluewallet.bip157.encodeGetCFilters
import io.bluewallet.bip157.hexToBytes
import io.bluewallet.bip157.BlockHeaderTip
import io.bluewallet.bip157.GetCFilters
import io.bluewallet.bip157.SyncInput

// Wire round-trip (hand-built Core-layout hex; stop hash is internal byte order).
val wireHex =
    "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"
val getcfilters = decodeGetCFilters(hexToBytes(wireHex))
println(getcfilters.startHeight) // 0
println(bytesToHex(encodeGetCFilters(getcfilters)) == wireHex) // true

// Sans-I/O sync: caller supplies block hashes; library emits outbound P2P intents.
val blockHash = hexToBytes(
    "43497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
)
val sync = createFilterSync(blockHashAt = { blockHash })
sync.push(SyncInput.SetTip(BlockHeaderTip(height = 0, hash = blockHash)))
val started = sync.push(SyncInput.Start)
println(started.send[0].command) // "getcfheaders"
```

Filter verification and header chaining use in-package `filterHash` / `filterHeader`. Your app wires `SyncOutput.send` into whatever transport speaks BIP-157 commands.

### Compose with BIP-324

Peers must advertise `NODE_COMPACT_FILTERS` (`1 shl 6`). BIP-157 commands use BIP-324 short IDs 22–27 (`BIP157_SHORT_IDS`).

```kotlin
import io.bluewallet.bip157.encodeOutbound

for (intent in send) {
    val encoded = encodeOutbound(intent)
    // encoded.shortId + encoded.payload → BIP-324 opaque frame
}
```

Inbound BIP-157 messages arrive as opaque payloads; decode with `decodeCFilter` / `decodeCFHeaders` / `decodeCFCheckpt`.

### Block hash and filter header endianness

P2P wire payloads use **internal** byte order (raw `SHA256d` output) for `hash256` fields such as `stopHash` and `blockHash`.

RPC/explorer block hashes and BIP-158 test vectors use **display order** (byte-reversed). When comparing against vector hex or chaining headers, convert with `displayHashToInternal()`.

`createFilterSync` requires `tip.hash` to equal `blockHashAt(tip.height)` (both internal order). Request ranges follow Core disconnect rules: `stopHeight - startHeight < 1000` (filters) or `< 2000` (headers).

## Test vectors

- **Wire** — Hand-built hex under `testdata/wire/` (layouts aligned with Bitcoin Core serializers).
- **Headers** — `testdata/headers/from-testnet-19.json` derives filter-header rows from the official BIP-158 `testnet-19.json` fixtures (10 blocks).
- **Sync** — Scripted peer replies in common tests (happy path, mismatch rejection).

```bash
./gradlew :bip157:jvmTest
```

## Non-goals

- **GCS construction/matching** — use a BIP-158 library (or equivalent) at the app layer
- **TCP / BIP-324 transport** — owned by a BIP-324 library and app adapters
- **Wallet, UTXO, or key management**
- **Serving filters** (`NODE_COMPACT_FILTERS` server role)
- **Full consensus header sync** — callers supply a validated block-header tip/chain view
