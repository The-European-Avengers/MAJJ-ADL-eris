# ML Inference Time Experiment on Android

## Objective
Evaluate the performance of the Machine Learning model integrated into the mobile app by measuring, separately:

1. Data preprocessing time.
2. TFLite model loading time.
3. Pure model inference time.

## Technical Context
- Platform: Android (Kotlin).
- ML runtime: TensorFlow Lite (LiteRT).
- Model used: `carbon_model_cnn.tflite`.
- Data flow: carbon history -> feature processing -> inference -> 24-value prediction.

## Experiment Design
The benchmark runs when the main screen starts and performs:

1. **Preprocessing**
   - Runs `FeatureProcessor.processData(records)`.
   - Measures time using a high-resolution clock (`System.nanoTime`).

2. **Model loading**
   - Creates a new `CarbonModelPredictor(context)` instance.
   - Measures full initialization time.

3. **Warmup**
   - Executes 20 warmup inferences.
   - These are not included in the final results.

4. **Pure inference (main benchmark)**
   - Executes 100 inferences with the same input tensor.
   - Measures each inference individually in milliseconds.

## Reported Metrics
The following inference metrics are computed and reported:

- Mean (ms)
- Median (ms)
- p95 (ms)
- Standard deviation (ms)

Additionally, the following are reported:
- Preprocessing time (ms)
- Model loading time (ms)

## Result Output
Results are published in 3 places:

1. **App screen** (status/result text).
2. **Logcat** with tag: `ML_BENCHMARK`.
3. **Internal file**: `ml_benchmark.txt`.

Path inside the app:

`/data/data/com.example.myapplication/files/ml_benchmark.txt`

## How to Extract the File on Emulator/Device
From Windows PowerShell, in the `platform-tools` folder:

```powershell
.\adb pull /data/data/com.example.myapplication/files/ml_benchmark.txt
```

If there are permission restrictions, use this `run-as` alternative:

```powershell
.\adb shell "run-as com.example.myapplication cat files/ml_benchmark.txt" > ml_benchmark.txt
```

## Recommended Interpretation
- **High preprocessing time**: optimize feature generation or reduce per-iteration operations.
- **High model loading time**: keep a single reusable predictor instance.
- **High p95 relative to median**: significant variability (possible CPU/memory pressure).

## Recommended Conditions for Comparing Results
- Run in Release build for more realistic measurements.
- Test on a physical device in addition to an emulator.
- Keep conditions similar (battery/temperature/system load).
- Repeat tests with the same input for comparability.

## Current Limitations
- Benchmark uses a single model configuration and thread setup.
- It does not yet compare accelerators (NNAPI/GPU) or multiple batch sizes.

## Suggested Next Steps
1. Compare 1, 2, and 4 CPU threads.
2. Add an option to manually trigger the benchmark from a button.
3. Save historical results with timestamps for longitudinal analysis.



C:\Users\ROGSTRIX\AppData\Local\Android\Sdk\platform-tools>.\adb shell "run-as com.example.myapplication cat files/ml_benchmark.txt" > ml_benchmark.txt

dir ml_benchmark.txt 