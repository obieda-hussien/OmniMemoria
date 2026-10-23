#!/usr/bin/env python3
import unittest

from ocr_benchmark_ci_simulation import (
    CATEGORIES, ENGINES, IMAGE_COUNT, LABEL, PROFILES, WORKERS,
    image_manifest, simulate_engine,
)


class OcrBenchmarkScenarioTest(unittest.TestCase):
    def test_manifest_has_sixty_balanced_images_near_12mp(self):
        images = image_manifest(20261023)
        self.assertEqual(IMAGE_COUNT, len(images))
        self.assertEqual(CATEGORIES,
                         {key: sum(1 for row in images if row["category"] == key) for key in CATEGORIES})
        self.assertTrue(all(10.0 <= row["megapixels"] <= 13.0 for row in images))
        self.assertEqual(len({row["image_id"] for row in images}), IMAGE_COUNT)

    def test_two_worker_multi_round_schedule_and_synthetic_metrics(self):
        images = image_manifest(42)
        for index, name in enumerate(ENGINES):
            rows, summary = simulate_engine(PROFILES[name], images, 42 + index, 3, WORKERS)
            self.assertEqual(IMAGE_COUNT * 3, len(rows))
            self.assertEqual({LABEL}, {row["record_kind"] for row in rows})
            self.assertEqual({WORKERS}, {row["worker_count"] for row in rows})
            self.assertEqual(3, summary["rounds"])
            self.assertGreater(summary["simulated_images_per_second_median"], 0)
            self.assertGreaterEqual(summary["simulated_p95_latency_ms"], summary["simulated_p50_latency_ms"])
            self.assertGreater(summary["simulated_peak_pss_mib_max"], 0)
            self.assertIn("Scenario output", summary["note"])

    def test_repeatable_seed(self):
        images = image_manifest(123)
        a, sa = simulate_engine(PROFILES[ENGINES[0]], images, 123, 3, WORKERS)
        b, sb = simulate_engine(PROFILES[ENGINES[0]], images, 123, 3, WORKERS)
        self.assertEqual(a, b)
        self.assertEqual(sa, sb)

    def test_profile_assumptions_are_explicit(self):
        for profile in PROFILES.values():
            self.assertGreater(profile.ocr_ms["document"], 0)
            self.assertGreater(profile.base_pss_mib, 0)
            self.assertGreaterEqual(profile.region_recall, 0)
            self.assertLessEqual(profile.region_recall, 1)


if __name__ == "__main__":
    unittest.main()
