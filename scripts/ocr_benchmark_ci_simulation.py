#!/usr/bin/env python3
"""CI-only contract simulation for the OmniMemoria OCR benchmark.

This intentionally DOES NOT execute OCR and MUST NOT be reported as device performance.
It validates scheduling, metric aggregation, output schemas, and the requested 60-image /
2-worker configuration using deterministic synthetic records.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from statistics import mean
from typing import Any

IMAGE_COUNT = 60
WORKER_COUNT = 2
CATEGORIES = ("document", "screenshot", "skewed")
PER_CATEGORY = 20
ENGINES = ("omnimemoria-current", "indra-1.1")
SIMULATION_LABEL = "SIMULATION_ONLY_NOT_OCR_NOT_DEVICE_BENCHMARK"


def percentile(values: list[float], p: float) -> float:
    if not values:
        raise ValueError("percentile requires at least one value")
    ordered = sorted(values)
    rank = (len(ordered) - 1) * p
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return ordered[low]
    return ordered[low] + (ordered[high] - ordered[low]) * (rank - low)


def synthetic_records() -> list[dict[str, Any]]:
    """Create deterministic contract fixtures; timings are deliberately synthetic."""
    records: list[dict[str, Any]] = []
    for engine_index, engine in enumerate(ENGINES):
        for image_index in range(IMAGE_COUNT):
            category = CATEGORIES[image_index // PER_CATEGORY]
            # Stable, non-random values let CI catch metric regressions without implying
            # any real performance or accuracy for either OCR engine.
            decode_ms = 8.0 + (image_index % 7) * 1.5
            ocr_ms = 100.0 + engine_index * 17.0 + (image_index % 11) * 13.0
            queue_wait_ms = float((image_index % WORKER_COUNT) * 3)
            total_ms = decode_ms + ocr_ms
            records.append({
                "image_id": f"{category}-{image_index % PER_CATEGORY + 1:02d}",
                "category": category,
                "engine": engine,
                "round": 1,
                "worker_count": WORKER_COUNT,
                "width": 4000,
                "height": 3000,
                "decode_ms": round(decode_ms, 3),
                "queue_wait_ms": round(queue_wait_ms, 3),
                "ocr_ms": round(ocr_ms, 3),
                "total_ms": round(total_ms, 3),
                "pss_before_kb": None,
                "pss_peak_sampled_kb": None,
                "thermal_status_start": None,
                "thermal_status_end": None,
                "arabic_cer": None,
                "arabic_wer": None,
                "region_precision": None,
                "region_recall": None,
                "region_f1": None,
                "url_exact_match": None,
                "url_char_completeness": None,
                "zxing_detected": None,
                "zxing_content_match": None,
                "zxing_ms": None,
                "error": "",
                "record_kind": SIMULATION_LABEL,
            })
    return records


def summarize(records: list[dict[str, Any]]) -> dict[str, Any]:
    summaries = []
    for engine in ENGINES:
        rows = [row for row in records if row["engine"] == engine]
        if len(rows) != IMAGE_COUNT:
            raise AssertionError(f"{engine}: expected {IMAGE_COUNT} records, got {len(rows)}")
        elapsed_ms = sum(float(row["total_ms"]) for row in rows)
        # CI contract uses a conservative serial-equivalent schedule duration, not a
        # prediction of native worker overlap. Real device runner must use wall-clock time.
        serial_equivalent_seconds = elapsed_ms / WORKER_COUNT / 1000.0
        times = [float(row["total_ms"]) for row in rows]
        summaries.append({
            "engine": engine,
            "image_count": len(rows),
            "worker_count": WORKER_COUNT,
            "synthetic_p50_ms": round(percentile(times, 0.50), 3),
            "synthetic_p90_ms": round(percentile(times, 0.90), 3),
            "synthetic_p95_ms": round(percentile(times, 0.95), 3),
            "synthetic_mean_ms": round(mean(times), 3),
            "synthetic_serial_equivalent_images_per_second": round(
                IMAGE_COUNT / serial_equivalent_seconds, 6
            ),
            "measured_device_wall_clock_ms": None,
            "measured_peak_pss_kb": None,
            "measured_thermal_status": None,
            "accuracy_metrics": None,
            "note": "Synthetic contract data only; no OCR engine was run.",
        })
    return {
        "status": "simulation",
        "label": SIMULATION_LABEL,
        "device": None,
        "configuration": {
            "image_count": IMAGE_COUNT,
            "target_resolution": "approximately 12MP (synthetic fixture metadata only)",
            "workers": WORKER_COUNT,
            "categories": {category: PER_CATEGORY for category in CATEGORIES},
            "engines": list(ENGINES),
        },
        "metrics_are_real_device_measurements": False,
        "summaries": summaries,
        "records": len(records),
    }


def write_outputs(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    records = synthetic_records()
    report = summarize(records)
    csv_path = output_dir / "ocr-benchmark-ci-simulation.csv"
    json_path = output_dir / "ocr-benchmark-ci-simulation.json"
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(records[0].keys()))
        writer.writeheader()
        writer.writerows(records)
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: schema/scheduling simulation; records={report['records']}; engines={len(ENGINES)}")
    print(f"Explicit label: {SIMULATION_LABEL}")
    print("No OCR inference, Android device measurement, accuracy, memory, or thermal measurement occurred.")
    print(f"CSV: {csv_path}")
    print(f"JSON: {json_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="benchmark-ci-results")
    args = parser.parse_args()
    write_outputs(Path(args.output_dir))


if __name__ == "__main__":
    main()
