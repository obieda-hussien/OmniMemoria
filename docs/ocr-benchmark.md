# OmniMemoria OCR Benchmark: Android plan and CI simulation

## Target experiment

- Device: Infinix X689, Helio G85, Android 11 / API 30.
- Dataset: 60 images at native resolution near 12 MP: 20 Arabic/mixed-language documents, 20 screenshots, 20 skewed/oblique photos.
- Concurrency: two application-level workers. Record ONNX/native internal thread counts separately.
- Engines: current OmniMemoria pipeline and a complete Android-compatible Indra OCR 1.1 pipeline.
- QR: ZXing is measured independently from both OCR engines.

## Status and guardrails

The GitHub-hosted CI simulation is a contract test only. It emits deterministic synthetic timing rows to validate the result schema and aggregation code. It does not execute either OCR engine, and its numbers must never be used to claim speed, accuracy, memory, or thermal performance.

Indra is not considered integrated merely because ONNX Runtime is present. A valid comparison requires its complete preprocessing, detection, recognition, reading-order/postprocessing pipeline and pinned model assets to run locally on Android. Until that adapter exists and passes parity tests, the Indra fields in a real-device report must remain unavailable rather than being filled with estimates.

## Dataset and ground truth

Keep the private test images outside the repository unless redistribution rights are clear. Create a local manifest with one row per image containing:
- stable ID and category;
- image width/height and SHA-256;
- reference UTF-8 text;
- expected text-region boxes in normalized coordinates;
- exact expected URLs;
- expected QR/barcode payloads and their boxes.

No accuracy score is valid without this ground truth. Use the exact same source image bytes for both engines.

## Real-device run protocol

1. Install the same benchmark APK and pinned model assets on the X689. Disable network access for inference after model installation.
2. Record build SHA, Android build, model hashes, battery/charging state, free storage and baseline thermal status.
3. Run one unmeasured warm-up per engine; separately record cold model initialization.
4. Run three measured rounds per engine, alternating engine order. Use exactly two application workers and record engine-internal thread settings.
5. Capture monotonic per-image queue wait, decode, inference and total times; also capture wall-clock duration for the whole 60-image batch.
6. Sample app PSS and Android thermal status throughout. Treat PSS peak as the maximum observed sample, not a guaranteed instantaneous peak. Do not label battery temperature as CPU temperature.
7. Run ZXing as a separate pass on all 60 images. Include images with no code to measure false positives.
8. Export raw CSV and JSON before computing summaries. Preserve failures and timeouts.

## Metrics

- Latency: P50/P90/P95 and mean, separately for cold-start and warmed runs.
- Throughput: 60 / wall-clock seconds, never computed from the sum of per-image durations when workers overlap.
- Arabic text: CER and WER after a documented, conservative Unicode normalization policy.
- Text regions: precision/recall/F1 at a predeclared IoU threshold (default 0.5). Normalize both engines to the same box granularity first.
- URLs: exact match and character completeness; do not normalize away meaningful URL punctuation.
- QR: detection recall, false-positive count, exact payload match and ZXing-only latency.
- Thermal stability: Android thermal status trend and latency drift over the run. Report unavailable CPU temperatures as unavailable.

## CI simulation

Run:

```bash
python3 scripts/test_ocr_benchmark_ci_simulation.py
python3 scripts/ocr_benchmark_ci_simulation.py --output-dir benchmark-ci-results
```

The CI artifacts are intentionally labelled `SIMULATION_ONLY_NOT_OCR_NOT_DEVICE_BENCHMARK`. They prove only that the reporting contract is internally consistent. Real performance numbers require the device experiment above.
