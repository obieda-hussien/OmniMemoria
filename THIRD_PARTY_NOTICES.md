# Third-party notices

## PaddleOCR Android inference source and PP-OCRv5 Arabic models

This project vendors source files from the official PaddleOCR Android ONNX Runtime sample under the Apache License 2.0:
https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android

Model artifacts are obtained on demand from the official PaddlePaddle Hugging Face organization and are published under Apache-2.0:
- PP-OCRv5 mobile detector ONNX: https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx
- Arabic PP-OCRv5 mobile recognizer ONNX: https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx
- Recognition dictionary: https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec

The two ONNX files are SHA-256 checked before installation. The dictionary YAML is downloaded from a pinned repository revision and validated for expected model/configuration markers.

See `LICENSES/PaddleOCR-Apache-2.0.txt` for the full source license. ONNX Runtime and OpenCV remain separately licensed third-party dependencies; consult their upstream notices when redistributing builds.
