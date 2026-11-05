# Bink decoding and playback

Original build notes, translated into English. These describe the upstream build's implementation and recorded validation; they are not new validation claims for this English APK.
This document covers Linux/Android decoding, ABI compatibility, background work and dependency licensing. The Android packaging script also includes it as the FFmpeg build notes.

## Implementation and formats

The game retains the original Bink ABI, bwMovie, RAGE file callbacks, audio provider and texture uploads.
FFmpeg 8.0.1 decodes Bink 1 video and the older Bink DCT/RDFT audio. A separate CPU adapter decodes mission-video KB2g/Bink 2.2 video.
Newer SDK headers are not used with the old ABI: plane-member order and frame-buffer counts differ. The supplied 2026 SDK also lacks the expand.h required for Bink 1 and uses NO_BINK10_SUPPORT.

FFmpeg enables only the required Bink video, audio and PCM conversion. Networking, tools, unrelated decoders, GPL and nonfree components are disabled.
Download sources and SHA-256 are pinned in [BinkFfmpeg.cmake](../../src/dev_ng/cmake/BinkFfmpeg.cmake). Downloads are cached in target/downloads; complete dependency source is in the build directory under bink-ffmpeg/source.

The KB2g core comes from the supplied Bink2Source-2026.03.31.7z. Its provenance, checksums and original notices are retained under [src/dev_ng/rage/suite/src/bink/native2/](../../src/dev_ng/rage/suite/src/bink/native2). It is maintained separately from FFmpeg/Bink 1.

bink1_decoder validates containers, frame indexes and audio-track sizes, producing Y/Cr/Cb/A planes and interleaved S16 PCM.
At most four tracks are selected. Queues are bounded; stalled audio consumption reports dropped data instead of blocking rendering.
FFmpeg buffers are freed by their native allocator and are not counted as RAGE allocations.
Plane pointers and strides are reacquired on every texture lock to avoid writing stale addresses after a discard map.

The color matrix follows each file's BINKYCRCBNEW flag; switching back to older content restores the original parameters.
The adapter rejects unsupported BIKk full-range color and retains the old SDK's keyframe and return-value semantics.
Pixel, PCM and format boundaries are documented in [NUM-014..016](NUMERICAL_COMPATIBILITY.md#num-014).

## Background decoding contract

Each open movie has one worker for reading, audio-track indexing, keyframe seeking and decoding.
Rendering only polls completed results and uploads separate planes; RAGE audio callbacks stay on their original thread.
Only the latest request and one pending result are retained. New seeks cancel old work at the next I/O boundary; old results cannot overwrite a newer channel.
Closing waits for an entered file callback before closing its file. It cannot interrupt a single read already blocked inside the provider.

Multi-track indexes are committed per complete frame, so cancellation does not corrupt subsequent timestamps.
Completed seeks reset the playback clock. Non-looping playback uploads the final frame before stopping. Linux pending seeks are coalesced to the last target.
Pause, looping and storage lifetime retain their original contracts.

Two channels on ordinary televisions are original game behavior. Mission TV_HLP_LEST1B_1 uses its original script: left/right changes channels, up/down changes volume, and INPUTGROUP_MOVE/INPUT_PHONE prompts are routed appropriately.
Input is withdrawn when the mission withdraws its prompt; no extra exit action is added.

## Validation scope recorded by the original build

The standalone [tests/bink1](../../src/dev_ng/tests/bink1) project covers real Bink 1/KB2g full playback, random seeking, image/audio output, short reads, invalid containers and deliberately blocked I/O.
GTAV_BINK_BUILD_JOBS controls FFmpeg build parallelism and defaults to 1; an outer --parallel does not override it.

The original notes report passing historical development-sanitizer, ARM64 decoder and background regression checks.
Mission videos are lester1b_1.bik and lester1b_2.bik; passing ordinary programs does not replace testing these files.
The complete Story Mode mission flow still requires acceptance testing, as described in the [platform records](../records/platform-validation.md).
Rerun affected inputs when decoding or related behavior changes; this does not automatically require starting the entire game.

## Licensing and distribution materials

FFmpeg is used under LGPL 2.1 or later, and its original license is packaged with the APK.
Distributing statically linked products also requires the corresponding library source, build modifications and materials needed to replace/relink the library. A source URL alone is not a substitute for these materials.

Bink 1/audio does not use SDK source. The separate Bink 2 video core comes from the supplied 2026 SDK and retains its original notices; it is not FFmpeg or LGPL code.
