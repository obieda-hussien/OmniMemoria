#!/usr/bin/env python3
"""Scenario-based discrete-event OCR workload simulator for CI.

This is a model, NOT Android/Helio G85 execution. All engine performance, memory,
thermal and accuracy parameters are explicit scenario assumptions. Results must
never be labelled measured or used as evidence of actual Indra/OmniMemoria quality.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
from dataclasses import asdict, dataclass
from pathlib import Path
from statistics import mean, median
from typing import Any

SEED = 20261023
IMAGE_COUNT = 60
WORKERS = 2
CATEGORIES = {"document": 20, "screenshot": 20, "skewed": 20}
LABEL = "SCENARIO_SIMULATION_NOT_DEVICE_MEASUREMENT"
ENGINES = ("omnimemoria-current", "indra-1.1")


@dataclass(frozen=True)
class EngineProfile:
    name: str
    # Hypothetical CPU time in ms for 12MP input, by image class.
    ocr_ms: dict[str, float]
    # Hypothetical PSS increment at idle and per active worker (MiB).
    base_pss_mib: float
    per_worker_mib: float
    # Scenario-only probabilities; not measured quality.
    arabic_char_error: float
    region_recall: float
    region_precision: float
    url_exact_probability: float
    # Memory/thermal behavior assumptions.
    transient_mib_per_active_worker: float


PROFILES = {
    "omnimemoria-current": EngineProfile(
        "omnimemoria-current",
        {"document": 440, "screenshot": 390, "skewed": 610},
        310, 115, 0.105, 0.79, 0.86, 0.73, 42,
    ),
    "indra-1.1": EngineProfile(
        "indra-1.1",
        {"document": 560, "screenshot": 520, "skewed": 690},
        390, 150, 0.075, 0.87, 0.88, 0.82, 58,
    ),
}


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    k = (len(ordered) - 1) * p
    lo, hi = math.floor(k), math.ceil(k)
    return ordered[lo] if lo == hi else ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo)


def image_manifest(seed: int) -> list[dict[str, Any]]:
    rng = random.Random(seed)
    items = []
    idx = 0
    for category, count in CATEGORIES.items():
        for _ in range(count):
            idx += 1
            # Keep around 12MP while varying aspect ratio and scale.
            width = rng.choice([4000, 4032, 4096, 3840])
            height = max(1800, round(12_000_000 / width))
            pixels_mp = width * height / 1_000_000
            complexity = {
                "document": rng.uniform(0.75, 1.25),
                "screenshot": rng.uniform(0.65, 1.35),
                "skewed": rng.uniform(1.0, 1.85),
            }[category]
            arabic_fraction = rng.uniform(0.45, 0.95)
            url_count = rng.choices([0, 1, 2, 3], weights=[0.4, 0.35, 0.2, 0.05])[0] if category == "screenshot" else rng.choices([0, 1, 2], weights=[0.7, 0.25, 0.05])[0]
            qr_count = rng.choices([0, 1, 2], weights=[0.7, 0.27, 0.03])[0]
            items.append({
                "image_id": f"{category}-{idx:03d}",
                "category": category,
                "width": width,
                "height": height,
                "megapixels": round(pixels_mp, 3),
                "complexity_factor": round(complexity, 4),
                "arabic_fraction": round(arabic_fraction, 4),
                "url_count_gt": url_count,
                "qr_count_gt": qr_count,
            })
    return items


def simulate_engine(engine: EngineProfile, images: list[dict[str, Any]], seed: int,
                    rounds: int, workers_count: int) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    rng = random.Random(seed + sum(ord(c) for c in engine.name))
    all_rows: list[dict[str, Any]] = []
    round_summaries = []
    for round_no in range(1, rounds + 1):
        # A round is one complete 60-image batch. Event simulation uses two workers,
        # queue scheduling, shared CPU contention, rising thermal pressure and memory.
        worker_free = [0.0] * workers_count
        events: list[dict[str, Any]] = []
        thermal_pressure = 0.0
        pss_peak = engine.base_pss_mib
        throttled_ms = 0.0
        for image in images:
            worker = min(range(workers_count), key=lambda i: worker_free[i])
            queue_start = worker_free[worker]
            base = engine.ocr_ms[image["category"]]
            mp_scale = image["megapixels"] / 12.0
            jitter = rng.lognormvariate(0, 0.11)
            # CPU contention rises with active workers. Thermal pressure slowly
            # accumulates and reduces effective throughput after a threshold.
            contention = 1.0 + 0.13 * max(0, workers_count - 1)
            thermal_factor = 1.0 + max(0.0, thermal_pressure - 0.65) * 0.75
            decode_ms = (24 + image["megapixels"] * 5.2) * rng.uniform(0.85, 1.2)
            ocr_ms = base * mp_scale * image["complexity_factor"] * jitter * contention * thermal_factor
            post_ms = 22 + 14 * image["complexity_factor"] + rng.uniform(0, 12)
            duration = decode_ms + ocr_ms + post_ms
            start = queue_start
            finish = start + duration
            worker_free[worker] = finish
            thermal_pressure = min(1.35, thermal_pressure + duration / 10000 * (0.72 + 0.12 * workers_count))
            if thermal_factor > 1.02:
                throttled_ms += duration
            pss_now = engine.base_pss_mib + engine.per_worker_mib * workers_count + engine.transient_mib_per_active_worker * rng.uniform(0.55, 1.3)
            pss_peak = max(pss_peak, pss_now)
            char_error = min(0.45, max(0.005, rng.gauss(engine.arabic_char_error * image["complexity_factor"], 0.018)))
            region_recall = min(1.0, max(0.0, rng.gauss(engine.region_recall - 0.045 * (image["complexity_factor"] - 1), 0.045)))
            region_precision = min(1.0, max(0.0, rng.gauss(engine.region_precision, 0.035)))
            f1 = 2 * region_recall * region_precision / max(1e-9, region_recall + region_precision)
            url_exact = None if image["url_count_gt"] == 0 else (rng.random() < engine.url_exact_probability / image["complexity_factor"])
            qr_detected = min(image["qr_count_gt"], sum(1 for _ in range(image["qr_count_gt"]) if rng.random() < 0.91))
            qr_ms = 5 + image["megapixels"] * 1.8 + image["qr_count_gt"] * 7 + rng.uniform(0, 8)
            events.append({
                **image,
                "engine": engine.name,
                "round": round_no,
                "worker_count": workers_count,
                "worker_id": worker,
                "queue_wait_ms": round(start, 3),
                "decode_ms": round(decode_ms, 3),
                "ocr_ms": round(ocr_ms, 3),
                "postprocess_ms": round(post_ms, 3),
                "image_total_ms": round(duration, 3),
                "finish_ms": round(finish, 3),
                "synthetic_arabic_cer": round(char_error, 5),
                "synthetic_arabic_wer": round(min(0.95, char_error * rng.uniform(1.5, 2.5)), 5),
                "synthetic_region_precision": round(region_precision, 5),
                "synthetic_region_recall": round(region_recall, 5),
                "synthetic_region_f1": round(f1, 5),
                "url_count_gt": image["url_count_gt"],
                "synthetic_all_urls_exact": None if url_exact is None else bool(url_exact),
                "qr_count_gt": image["qr_count_gt"],
                "synthetic_qr_detected_count": qr_detected,
                "synthetic_zxing_ms": round(qr_ms, 3),
                "synthetic_pss_mib_sample": round(pss_now, 2),
                "synthetic_thermal_pressure": round(thermal_pressure, 5),
                "synthetic_throttling_active": thermal_factor > 1.02,
                "error": "",
                "record_kind": LABEL,
            })
        wall = max(worker_free)
        all_rows.extend(events)
        latencies = [e["image_total_ms"] for e in events]
        round_summaries.append({
            "round": round_no,
            "simulated_wall_clock_ms": round(wall, 3),
            "simulated_images_per_second": round(len(images) / (wall / 1000), 5),
            "p50_latency_ms": round(percentile(latencies, .50), 3),
            "p90_latency_ms": round(percentile(latencies, .90), 3),
            "p95_latency_ms": round(percentile(latencies, .95), 3),
            "simulated_peak_pss_mib": round(pss_peak, 2),
            "synthetic_throttling_fraction": round(throttled_ms / max(1, sum(latencies)), 5),
            "synthetic_mean_cer": round(mean(e["synthetic_arabic_cer"] for e in events), 5),
            "synthetic_mean_region_f1": round(mean(e["synthetic_region_f1"] for e in events), 5),
        })
    latencies = [r["image_total_ms"] for r in all_rows]
    summary = {
        "engine": engine.name,
        "images_per_round": len(images),
        "rounds": rounds,
        "workers": workers_count,
        "simulated_wall_clock_ms_median": round(median(r["simulated_wall_clock_ms"] for r in round_summaries), 3),
        "simulated_images_per_second_median": round(median(r["simulated_images_per_second"] for r in round_summaries), 5),
        "simulated_p50_latency_ms": round(percentile(latencies, .50), 3),
        "simulated_p90_latency_ms": round(percentile(latencies, .90), 3),
        "simulated_p95_latency_ms": round(percentile(latencies, .95), 3),
        "simulated_peak_pss_mib_max": max(r["simulated_peak_pss_mib"] for r in round_summaries),
        "synthetic_mean_arabic_cer": round(mean(r["synthetic_arabic_cer"] for r in all_rows), 5),
        "synthetic_mean_arabic_wer": round(mean(r["synthetic_arabic_wer"] for r in all_rows), 5),
        "synthetic_mean_region_f1": round(mean(r["synthetic_region_f1"] for r in all_rows), 5),
        "synthetic_url_exact_rate": round(mean([float(r["synthetic_all_urls_exact"]) for r in all_rows if r["synthetic_all_urls_exact"] is not None]), 5),
        "synthetic_qr_recall": round(sum(r["synthetic_qr_detected_count"] for r in all_rows) / max(1, sum(r["qr_count_gt"] for r in all_rows)), 5),
        "rounds_detail": round_summaries,
        "note": "Scenario output, not measured OCR/device data.",
    }
    return all_rows, summary


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="benchmark-ci-results")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--workers", type=int, default=WORKERS)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()
    if args.rounds < 3 or args.rounds > 30:
        parser.error("--rounds must be between 3 and 30")
    if args.workers != 2:
        parser.error("This benchmark scenario is configured for exactly 2 workers")
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    images = image_manifest(args.seed)
    records: list[dict[str, Any]] = []
    summaries = []
    for i, engine_name in enumerate(ENGINES):
        rows, summary = simulate_engine(PROFILES[engine_name], images, args.seed + i * 101, args.rounds, args.workers)
        records.extend(rows)
        summaries.append(summary)
    # Independent QR workload: one ZXing-like pass per image; deliberately synthetic.
    rng = random.Random(args.seed + 9901)
    qr_records = []
    for image in images:
        detected = sum(1 for _ in range(image["qr_count_gt"]) if rng.random() < 0.91)
        qr_records.append({
            "image_id": image["image_id"],
            "qr_count_gt": image["qr_count_gt"],
            "synthetic_zxing_detected_count": detected,
            "synthetic_zxing_ms": round(5 + image["megapixels"] * 1.8 + image["qr_count_gt"] * 7 + rng.uniform(0, 8), 3),
            "record_kind": LABEL,
        })
    report = {
        "status": "scenario_simulation",
        "label": LABEL,
        "seed": args.seed,
        "target_device_modelled_not_emulated": "Infinix X689 / Helio G85 / Android 11",
        "runtime": "Python discrete-event simulation on GitHub-hosted runner; no Android OCR execution",
        "configuration": {
            "image_count": IMAGE_COUNT,
            "target_resolution": "approximately 12MP (synthetic dimensions, 4000x3000 class)",
            "workers": args.workers,
            "rounds": args.rounds,
            "categories": CATEGORIES,
            "engines": list(ENGINES),
            "profiles_are_empirical": False,
            "profile_note": "All latency, RAM, accuracy, URL, QR and thermal parameters are explicit illustrative assumptions, not measured facts.",
        },
        "dataset_manifest_sha256": hashlib.sha256(json.dumps(images, sort_keys=True).encode()).hexdigest(),
        "summaries": summaries,
        "independent_zxing_scenario": {
            "synthetic_qr_recall": round(sum(r["synthetic_zxing_detected_count"] for r in qr_records) / max(1, sum(r["qr_count_gt"] for r in qr_records)), 5),
            "records": qr_records,
        },
        "records": len(records),
        "metrics_are_real_device_measurements": False,
        "warnings": [
            "No OCR engine was run; output is generated from scenario assumptions.",
            "GitHub-hosted x86 emulator/runner cannot reproduce Helio G85 throughput or thermal characteristics.",
            "Synthetic accuracy metrics are not quality claims and must not be used to select a production OCR engine.",
        ],
    }
    csv_path = output / "ocr-benchmark-scenario-simulation.csv"
    json_path = output / "ocr-benchmark-scenario-simulation.json"
    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(records[0].keys()))
        writer.writeheader()
        writer.writerows(records)
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Scenario simulation PASS: {len(images)} images x {len(ENGINES)} engines x {args.rounds} rounds")
    for summary in summaries:
        print(f"{summary['engine']}: simulated throughput={summary['simulated_images_per_second_median']} img/s; P95={summary['simulated_p95_latency_ms']} ms; synthetic PSS peak={summary['simulated_peak_pss_mib_max']} MiB")
    print(f"Label: {LABEL}")
    print("These values are not device measurements or actual OCR accuracy.")


if __name__ == "__main__":
    main()
