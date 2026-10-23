#!/usr/bin/env python3
import unittest

from ocr_benchmark_ci_simulation import (
    CATEGORIES,
    ENGINES,
    IMAGE_COUNT,
    SIMULATION_LABEL,
    WORKER_COUNT,
    summarize,
    synthetic_records,
)


class OcrBenchmarkSimulationTest(unittest.TestCase):
    def test_requested_configuration_and_record_count(self):
        records = synthetic_records()
        self.assertEqual(IMAGE_COUNT * len(ENGINES), len(records))
        for engine in ENGINES:
            rows = [row for row in records if row["engine"] == engine]
            self.assertEqual(60, len(rows))
            self.assertEqual({WORKER_COUNT}, {row["worker_count"] for row in rows})
            self.assertEqual({SIMULATION_LABEL}, {row["record_kind"] for row in rows})
            self.assertEqual(set(CATEGORIES), {row["category"] for row in rows})

    def test_summary_never_claims_device_measurements(self):
        report = summarize(synthetic_records())
        self.assertFalse(report["metrics_are_real_device_measurements"])
        self.assertIsNone(report["device"])
        for engine in report["summaries"]:
            self.assertIsNone(engine["measured_device_wall_clock_ms"])
            self.assertIsNone(engine["measured_peak_pss_kb"])
            self.assertIsNone(engine["measured_thermal_status"])
            self.assertIsNone(engine["accuracy_metrics"])
            self.assertIn("no OCR engine was run", engine["note"])

    def test_percentiles_are_monotonic(self):
        for row in summarize(synthetic_records())["summaries"]:
            self.assertLessEqual(row["synthetic_p50_ms"], row["synthetic_p90_ms"])
            self.assertLessEqual(row["synthetic_p90_ms"], row["synthetic_p95_ms"])


if __name__ == "__main__":
    unittest.main()
