# Random-access data: behaviour contract

Every implementation of fluxo-io's random-access data, in Kotlin and in any future port (native TS/JS, Rust incl. WASM/WASI), must behave as stated here.
The executable form is `fluxo-io-rad/src/commonTest/kotlin/fluxo/io/rad/RadContract.kt`; a rule changes in both places together.

## Model

- **Data** is an immutable byte range `[0, size)`.
- A **handle** is what a factory or `share()` returns. It holds one ownership of the underlying resource and must be closed once. The resource is released when its last handle is closed.
- A **slice** (`slice(position, length)`) is a view of a sub-range of a handle or of another slice. It copies nothing, owns nothing, and closing it does nothing. It reads only while the handle it came from is open.
- `share()` returns a new handle over the same range; it keeps the data readable after the original closes.

## Reads

Positions in a slice are relative to the slice. "Bounds error" means the platform's index/range error (Kotlin `IndexOutOfBoundsException`), raised before any byte is written.

| Operation | Result |
|---|---|
| `readFrom(position, maxLength)` | a new array of `min(maxLength, size - position)` bytes; empty when `position == size` or `maxLength == 0` |
| `read(buffer, position, offset, maxLength)` | `-1` when `position >= size` (checked first, even for an empty buffer); otherwise the count of bytes copied into `buffer` at `offset`: `min(maxLength, buffer.size - offset, size - position)`, which may be 0 |
| `readFully(...)` | same arguments and results as `read`, but never returns fewer bytes than are available |
| `readAllBytes()` | the whole range |

- A negative position, a `position > size` for `readFrom`, a negative `maxLength`, or an `offset` outside `[0, buffer.size]` is a bounds error. Positions that do not fit 32 bits behave by their 64-bit value, never truncated.
- A rejected call writes nothing into the caller's buffer, and a read never writes past the bytes it reports.
- A read or transfer that makes no progress (the source returns 0 bytes, or the target accepts none) fails with an I/O error instead of retrying.

## Slices

- `slice(position, length)` requires `0 <= position`, `0 <= length`, `position + length <= size`; otherwise a bounds error. `length` defaults to `size - position`.
- Slices nest; a slice of a slice is bounded by its parent slice.
- A slice of size 0 is valid and reads as empty data.

## Lifetime

- **A closed handle never reads, and neither does any slice taken from it**, even while another handle keeps the same data open. Every read, `slice()` and `share()` on it fails with an I/O error that tells the caller to use `share()` for an independent handle. Rationale: otherwise a read-after-close succeeds or fails depending on unrelated handles, which hides the caller's bug.
- Closing a handle twice is a no-op the second time. It must never give back a second ownership, which would free data other handles still use.
- A close that races an in-flight read must not free the resource under that read: the release waits until the read ends, and the close itself returns without waiting.

## Async

- Async reads (`AsyncRandomAccessData`) follow the same rules and must never block the calling thread. An adapter over blocking data runs each call on a caller-chosen executor, rejects a context without one, and takes over the handle it wraps.

## Fixture

Implementations are verified against generated data, never stored files: `byte(i) = (i * 31 + 7) mod 256` for `i` in `[0, 256)`. No byte equals its index and byte 0 is not 0, so a shifted read or an untouched zeroed buffer cannot pass.
