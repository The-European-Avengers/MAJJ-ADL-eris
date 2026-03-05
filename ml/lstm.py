#!/usr/bin/env python3
"""
LSTM Carbon Intensity Forecaster — Denmark (TensorFlow/Keras + TFLite)
=======================================================================
V2: Enhanced with wind speed, solar radiation, electricity price, and RE%.

Predicts 24-hour-ahead carbon intensity (gCO2eq/kWh) for Denmark.
Trains on 2021–2024, tests on 2025, exports to TFLite for on-device use.

Usage:
    python lstm.py

Requirements:
    pip install tensorflow pandas numpy scikit-learn matplotlib
"""

import os
import glob
import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import warnings
import time

warnings.filterwarnings("ignore")

# ============================================================
# CONFIG — paths are relative to ml/ directory
# ============================================================
EMISSIONS_DIR = "../emissions-data"
WEATHER_DIR = "../weather"
PRICES_DIR = "../prices"

EMISSION_FILES = [
    "DK-2021-hourly.csv",
    "DK-2022-hourly.csv",
    "DK-2023-hourly.csv",
    "DK-2024-hourly.csv",
    "DK-2025-hourly.csv",
]
TARGET_COL = "Carbon intensity gCO₂eq/kWh (direct)"

# Model hyperparameters
LOOKBACK = 168        # 7 days of hourly data as input window
FORECAST_HORIZON = 24 # Predict next 24 hours
HIDDEN_SIZE = 64     # Increased from 64 — more features need more capacity
NUM_LAYERS = 2
DROPOUT = 0.2
BATCH_SIZE = 64
EPOCHS = 30           # More epochs — new features give more to learn
LEARNING_RATE = 0.001

TRAIN_YEARS = [2021, 2022, 2023]
TEST_YEARS = [2024]

RESULTS_DIR = "results"


# ============================================================
# DATA LOADING — Emissions
# ============================================================
def load_emissions(data_dir, filenames):
    """Load and concatenate emission CSV files."""
    dfs = []
    for fname in filenames:
        path = os.path.join(data_dir, fname)
        if not os.path.exists(path):
            print(f"  WARNING: {path} not found, skipping.")
            continue
        df = pd.read_csv(path)
        dfs.append(df)
        print(f"  Loaded {fname}: {len(df)} rows")
    if not dfs:
        raise FileNotFoundError(f"No emission files found in {data_dir}")
    combined = pd.concat(dfs, ignore_index=True)
    combined["datetime"] = pd.to_datetime(combined["Datetime (UTC)"])
    combined = combined.sort_values("datetime").reset_index(drop=True)
    return combined


# ============================================================
# DATA LOADING — Weather (DMI)
# ============================================================
def load_weather(weather_dir, years):
    """
    Load DMI wind and radiation CSVs, average across all stations per hour.
    Files: {year}_dmi_wind.csv, {year}_dmi_radiation.csv
    """
    # --- Wind ---
    wind_dfs = []
    for year in years:
        path = os.path.join(weather_dir, f"{year}_dmi_wind.csv")
        if os.path.exists(path):
            df = pd.read_csv(path)
            wind_dfs.append(df)
            print(f"  Loaded {year}_dmi_wind.csv: {len(df)} rows")
        else:
            print(f"  WARNING: {path} not found")

    if wind_dfs:
        wind_all = pd.concat(wind_dfs, ignore_index=True)
        wind_all["datetime"] = pd.to_datetime(wind_all["timeObserved"], format="mixed", utc=True)
        wind_all["datetime"] = wind_all["datetime"].dt.tz_localize(None)
        # Average across all stations for each hour
        wind_hourly = (wind_all.groupby("datetime")["mean_wind_speed"]
                       .mean().reset_index()
                       .rename(columns={"mean_wind_speed": "wind_speed"}))
    else:
        wind_hourly = pd.DataFrame(columns=["datetime", "wind_speed"])

    # --- Radiation ---
    rad_dfs = []
    for year in years:
        path = os.path.join(weather_dir, f"{year}_dmi_radiation.csv")
        if os.path.exists(path):
            df = pd.read_csv(path)
            rad_dfs.append(df)
            print(f"  Loaded {year}_dmi_radiation.csv: {len(df)} rows")
        else:
            print(f"  WARNING: {path} not found")

    if rad_dfs:
        rad_all = pd.concat(rad_dfs, ignore_index=True)
        rad_all["datetime"] = pd.to_datetime(rad_all["timeObserved"], format="mixed", utc=True)
        rad_all["datetime"] = rad_all["datetime"].dt.tz_localize(None)
        rad_hourly = (rad_all.groupby("datetime")["mean_radiation"]
                      .mean().reset_index()
                      .rename(columns={"mean_radiation": "solar_radiation"}))
    else:
        rad_hourly = pd.DataFrame(columns=["datetime", "solar_radiation"])

    # Merge wind and radiation
    if not wind_hourly.empty and not rad_hourly.empty:
        weather = pd.merge(wind_hourly, rad_hourly, on="datetime", how="outer")
    elif not wind_hourly.empty:
        weather = wind_hourly
    elif not rad_hourly.empty:
        weather = rad_hourly
    else:
        weather = pd.DataFrame(columns=["datetime", "wind_speed", "solar_radiation"])

    weather = weather.sort_values("datetime").reset_index(drop=True)
    return weather


# ============================================================
# DATA LOADING — Prices (ENTSO-E Day-Ahead)
# ============================================================
def load_prices(prices_dir, years):
    """
    Load ENTSO-E day-ahead price CSVs for DK1 and DK2, average them.
    Files: DayAheadPrices_DK1_*.csv, DayAheadPrices_DK2_*.csv
    """
    all_dfs = []

    for zone in ["DK1", "DK2"]:
        pattern = os.path.join(prices_dir, f"DayAheadPrices_{zone}_*.csv")
        files = sorted(glob.glob(pattern))
        for fpath in files:
            try:
                df = pd.read_csv(fpath)
                # Parse the MTU column: "31/12/2020 23:00:00 - 01/01/2021 00:00:00"
                df["datetime"] = pd.to_datetime(
                    df["MTU (UTC)"].str.split(" - ").str[0],
                    format="%d/%m/%Y %H:%M:%S"
                )
                df["zone"] = zone
                df = df[["datetime", "zone", "Day-ahead Price (EUR/MWh)"]].copy()
                df = df.rename(columns={"Day-ahead Price (EUR/MWh)": "price"})
                # Handle non-numeric prices
                df["price"] = pd.to_numeric(df["price"], errors="coerce")
                all_dfs.append(df)
                print(f"  Loaded {os.path.basename(fpath)}: {len(df)} rows")
            except Exception as e:
                print(f"  WARNING: Failed to parse {fpath}: {e}")

    if not all_dfs:
        return pd.DataFrame(columns=["datetime", "price_avg"])

    prices = pd.concat(all_dfs, ignore_index=True)

    # Average DK1 and DK2 prices per hour
    price_avg = (prices.groupby("datetime")["price"]
                 .mean().reset_index()
                 .rename(columns={"price": "price_avg"}))

    # Filter to requested years
    price_avg = price_avg[price_avg["datetime"].dt.year.isin(years)]
    price_avg = price_avg.sort_values("datetime").reset_index(drop=True)
    return price_avg


# ============================================================
# MERGE ALL DATA
# ============================================================
def merge_all_data(emissions_df, weather_df, price_df):
    """Merge emissions with weather and price data on datetime."""
    merged = emissions_df.copy()

    # Merge weather
    if not weather_df.empty:
        before = len(merged)
        merged = pd.merge(merged, weather_df, on="datetime", how="left")
        matched = merged["wind_speed"].notna().sum() if "wind_speed" in merged.columns else 0
        print(f"  Weather merge: {matched}/{before} hours matched")
    else:
        merged["wind_speed"] = np.nan
        merged["solar_radiation"] = np.nan

    # Merge prices
    if not price_df.empty:
        before = len(merged)
        merged = pd.merge(merged, price_df, on="datetime", how="left")
        matched = merged["price_avg"].notna().sum() if "price_avg" in merged.columns else 0
        print(f"  Price merge:   {matched}/{before} hours matched")
    else:
        merged["price_avg"] = np.nan

    # Ensure columns exist
    for col in ["wind_speed", "solar_radiation", "price_avg"]:
        if col not in merged.columns:
            merged[col] = np.nan

    return merged


# ============================================================
# PREPROCESSING
# ============================================================
def preprocess(df):
    """Add time features and handle missing values."""
    df = df.copy()

    # Cyclical time features
    df["hour_sin"] = np.sin(2 * np.pi * df["datetime"].dt.hour / 24)
    df["hour_cos"] = np.cos(2 * np.pi * df["datetime"].dt.hour / 24)
    df["dow_sin"] = np.sin(2 * np.pi * df["datetime"].dt.dayofweek / 7)
    df["dow_cos"] = np.cos(2 * np.pi * df["datetime"].dt.dayofweek / 7)
    df["month_sin"] = np.sin(2 * np.pi * df["datetime"].dt.month / 12)
    df["month_cos"] = np.cos(2 * np.pi * df["datetime"].dt.month / 12)

    # Drop rows where target is missing
    df = df.dropna(subset=[TARGET_COL])

    # Forward-fill then backward-fill weather/price gaps (small gaps are OK)
    for col in ["wind_speed", "solar_radiation", "price_avg",
                 "Renewable energy percentage (RE%)"]:
        if col in df.columns:
            df[col] = df[col].ffill().bfill()

    # If still NaN (entire column missing), fill with 0
    df = df.fillna(0)

    return df


def get_feature_columns(df):
    """
    Determine which features to use based on data availability.
    Always includes target + time features. Adds weather/price/RE% if available.
    """
    base_features = [
        TARGET_COL,
        "hour_sin", "hour_cos",
        "dow_sin", "dow_cos",
        "month_sin", "month_cos",
    ]

    optional_features = []

    # Check data availability (require >50% non-zero values)
    for col, label in [
        ("wind_speed", "Wind speed"),
        ("solar_radiation", "Solar radiation"),
        ("price_avg", "Electricity price"),
        ("Renewable energy percentage (RE%)", "Renewable %"),
    ]:
        if col in df.columns:
            nonzero_pct = (df[col] != 0).mean() * 100
            if nonzero_pct > 50:
                optional_features.append(col)
                print(f"    ✓ {label}: {nonzero_pct:.0f}% data available")
            else:
                print(f"    ✗ {label}: only {nonzero_pct:.0f}% data — skipping")
        else:
            print(f"    ✗ {label}: column not found — skipping")

    all_features = base_features + optional_features
    return all_features


# ============================================================
# SLIDING WINDOW DATASET
# ============================================================
def create_sequences(data, lookback, horizon):
    """Create (X, y) pairs using a sliding window."""
    X, y = [], []
    for i in range(len(data) - lookback - horizon + 1):
        X.append(data[i : i + lookback])
        y.append(data[i + lookback : i + lookback + horizon, 0])  # target = col 0
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.float32)


# ============================================================
# MODEL (Keras)
# ============================================================
def build_model(input_shape, output_size, hidden_size=128, num_layers=2, dropout=0.2):
    """LSTM model for multi-step forecasting."""
    model = tf.keras.Sequential()

    for i in range(num_layers):
        return_seq = i < num_layers - 1
        model.add(tf.keras.layers.LSTM(
            hidden_size,
            return_sequences=return_seq,
            input_shape=input_shape if i == 0 else None,
        ))
        model.add(tf.keras.layers.Dropout(dropout))

    model.add(tf.keras.layers.Dense(hidden_size, activation="relu"))
    model.add(tf.keras.layers.Dropout(dropout))
    model.add(tf.keras.layers.Dense(output_size))

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss="mse",
        metrics=["mae"],
    )
    return model


# ============================================================
# EVALUATION
# ============================================================
def inverse_target(values, scaler, n_features):
    """Inverse-scale the target column (index 0)."""
    n_samples, horizon = values.shape
    flat = values.reshape(-1, 1)
    dummy = np.zeros((flat.shape[0], n_features))
    dummy[:, 0] = flat[:, 0]
    inv = scaler.inverse_transform(dummy)[:, 0]
    return inv.reshape(n_samples, horizon)


def compute_metrics(preds, targets):
    """Compute MAE, RMSE, MAPE overall and per-horizon."""
    pf = preds.flatten()
    tf_ = targets.flatten()

    mae = mean_absolute_error(tf_, pf)
    rmse = np.sqrt(mean_squared_error(tf_, pf))
    mask = tf_ != 0
    mape = np.mean(np.abs((tf_[mask] - pf[mask]) / tf_[mask])) * 100

    horizon_mae, horizon_mape = [], []
    for h in range(preds.shape[1]):
        h_mae = mean_absolute_error(targets[:, h], preds[:, h])
        m = targets[:, h] != 0
        h_mape = np.mean(np.abs((targets[m, h] - preds[m, h]) / targets[m, h])) * 100
        horizon_mae.append(h_mae)
        horizon_mape.append(h_mape)

    return {"mae": mae, "rmse": rmse, "mape": mape,
            "horizon_mae": horizon_mae, "horizon_mape": horizon_mape}


# ============================================================
# TFLITE CONVERSION
# ============================================================
def convert_to_tflite(model, save_path):
    """Convert Keras model to TFLite for on-device inference."""
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]
    converter._experimental_lower_tensor_list_ops = False
    tflite_model = converter.convert()

    with open(save_path, "wb") as f:
        f.write(tflite_model)

    size_kb = os.path.getsize(save_path) / 1024
    print(f"  TFLite model saved: {save_path} ({size_kb:.1f} KB)")
    return save_path


def test_tflite(tflite_path, X_sample):
    """Quick sanity check that the TFLite model produces output."""
    try:
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        sample = X_sample[:1].astype(np.float32)
        interpreter.resize_tensor_input(input_details[0]["index"], sample.shape)
        interpreter.allocate_tensors()
        interpreter.set_tensor(input_details[0]["index"], sample)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]["index"])
        print(f"  TFLite sanity check — input: {sample.shape} -> output: {output.shape}")
        return output
    except RuntimeError as e:
        if "Flex delegate" in str(e) or "Select TensorFlow op" in str(e):
            print(f"  TFLite sanity check skipped — Flex delegate not available in desktop Python.")
            print(f"  This is expected. The model will work on Android with the select-tf-ops dependency.")
        else:
            raise


# ============================================================
# PLOTTING
# ============================================================
def plot_results(history, preds, targets, metrics, feature_cols, save_dir):
    os.makedirs(save_dir, exist_ok=True)

    # 1. Training curves
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.plot(history.history["loss"], label="Train Loss")
    ax.plot(history.history["val_loss"], label="Val Loss")
    ax.set_xlabel("Epoch")
    ax.set_ylabel("MSE Loss")
    ax.set_title("Training and Validation Loss")
    ax.legend()
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "training_curves.png"), dpi=150)
    plt.close()
    print(f"  Saved training_curves.png")

    # 2. Per-horizon error
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
    hours = range(1, FORECAST_HORIZON + 1)
    ax1.bar(hours, metrics["horizon_mae"], color="steelblue", alpha=0.8)
    ax1.set_xlabel("Forecast Hour")
    ax1.set_ylabel("MAE (gCO2eq/kWh)")
    ax1.set_title("MAE by Forecast Horizon")
    ax1.grid(True, alpha=0.3, axis="y")

    ax2.bar(hours, metrics["horizon_mape"], color="coral", alpha=0.8)
    ax2.set_xlabel("Forecast Hour")
    ax2.set_ylabel("MAPE (%)")
    ax2.set_title("MAPE by Forecast Horizon")
    ax2.grid(True, alpha=0.3, axis="y")
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "horizon_error.png"), dpi=150)
    plt.close()
    print(f"  Saved horizon_error.png")

    # 3. Sample predictions
    n_show = min(5, preds.shape[0])
    fig, axes = plt.subplots(n_show, 1, figsize=(14, 3 * n_show))
    if n_show == 1:
        axes = [axes]
    for i in range(n_show):
        ax = axes[i]
        hrs = range(1, FORECAST_HORIZON + 1)
        ax.plot(hrs, targets[i], "b-o", markersize=3, label="Actual", linewidth=1.5)
        ax.plot(hrs, preds[i], "r--s", markersize=3, label="Predicted", linewidth=1.5)
        ax.set_ylabel("gCO2eq/kWh")
        ax.set_title(f"Sample {i+1}: 24-Hour Forecast")
        ax.legend(loc="upper right")
        ax.grid(True, alpha=0.3)
    axes[-1].set_xlabel("Forecast Hour")
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "sample_predictions.png"), dpi=150)
    plt.close()
    print(f"  Saved sample_predictions.png")

    # 4. Scatter
    fig, ax = plt.subplots(figsize=(8, 8))
    pf, tf_ = preds.flatten(), targets.flatten()
    ax.scatter(tf_, pf, alpha=0.05, s=5, color="steelblue")
    lims = [min(tf_.min(), pf.min()), max(tf_.max(), pf.max())]
    ax.plot(lims, lims, "r--", linewidth=1.5, label="Perfect prediction")
    ax.set_xlabel("Actual (gCO2eq/kWh)")
    ax.set_ylabel("Predicted (gCO2eq/kWh)")
    ax.set_title(f"Predicted vs Actual  (MAE={metrics['mae']:.2f}, MAPE={metrics['mape']:.1f}%)")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal")
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "scatter_pred_vs_actual.png"), dpi=150)
    plt.close()
    print(f"  Saved scatter_pred_vs_actual.png")

    # 5. Feature list
    with open(os.path.join(save_dir, "features_used.txt"), "w") as f:
        f.write("Features used in this model run:\n")
        for i, col in enumerate(feature_cols):
            f.write(f"  [{i}] {col}\n")
        f.write(f"\nTotal: {len(feature_cols)} features\n")
        f.write(f"\nMetrics:\n")
        f.write(f"  MAE:  {metrics['mae']:.2f} gCO2eq/kWh\n")
        f.write(f"  RMSE: {metrics['rmse']:.2f} gCO2eq/kWh\n")
        f.write(f"  MAPE: {metrics['mape']:.1f}%\n")
    print(f"  Saved features_used.txt")


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("LSTM Carbon Intensity Forecaster v2 — Denmark")
    print("  With weather, price, and renewable % features")
    print("=" * 60)
    print(f"TensorFlow version: {tf.__version__}")
    gpus = tf.config.list_physical_devices("GPU")
    print(f"GPUs available: {len(gpus)}")
    print(f"Lookback: {LOOKBACK}h ({LOOKBACK//24}d) | Horizon: {FORECAST_HORIZON}h")
    print()

    all_years = TRAIN_YEARS + TEST_YEARS

    # -------------------------------------------------------
    # 1. Load all data sources
    # -------------------------------------------------------
    print("[1/8] Loading emission data...")
    emissions_df = load_emissions(EMISSIONS_DIR, EMISSION_FILES)
    print(f"  Total: {len(emissions_df)} rows")

    print("\n[2/8] Loading weather data (DMI)...")
    weather_df = load_weather(WEATHER_DIR, range(2020, 2026))
    print(f"  Total weather rows: {len(weather_df)}")

    print("\n[3/8] Loading price data (ENTSO-E)...")
    price_df = load_prices(PRICES_DIR, all_years)
    print(f"  Total price rows: {len(price_df)}")

    # -------------------------------------------------------
    # 2. Merge everything
    # -------------------------------------------------------
    print("\n[4/8] Merging datasets...")
    merged = merge_all_data(emissions_df, weather_df, price_df)
    print(f"  Merged dataset: {len(merged)} rows")

    # -------------------------------------------------------
    # 3. Preprocess
    # -------------------------------------------------------
    print("\n[5/8] Preprocessing...")
    merged = preprocess(merged)

    # Determine features based on data availability
    print("  Checking feature availability:")
    feature_cols = get_feature_columns(merged)
    print(f"  Using {len(feature_cols)} features: {feature_cols}")

    # Split into train and test
    train_df = merged[merged["datetime"].dt.year.isin(TRAIN_YEARS)].copy()
    test_df = merged[merged["datetime"].dt.year.isin(TEST_YEARS)].copy()
    print(f"  Train: {train_df['datetime'].min()} -> {train_df['datetime'].max()} ({len(train_df)} rows)")
    print(f"  Test:  {test_df['datetime'].min()} -> {test_df['datetime'].max()} ({len(test_df)} rows)")

    # --- Scale ---
    scaler = MinMaxScaler()
    train_scaled = scaler.fit_transform(train_df[feature_cols].values)
    test_scaled = scaler.transform(test_df[feature_cols].values)

    # Train / val split (last ~60 days = validation)
    val_size = 24 * 60
    train_data = train_scaled[:-val_size]
    val_data = train_scaled[-val_size - LOOKBACK:]

    print(f"  Train: {len(train_data)} | Val: {len(val_data)} | Test: {len(test_scaled)}")

    # -------------------------------------------------------
    # 4. Create sequences
    # -------------------------------------------------------
    print("\n[6/8] Creating sequences...")
    X_train, y_train = create_sequences(train_data, LOOKBACK, FORECAST_HORIZON)
    X_val, y_val = create_sequences(val_data, LOOKBACK, FORECAST_HORIZON)
    X_test, y_test = create_sequences(test_scaled, LOOKBACK, FORECAST_HORIZON)
    print(f"  X_train: {X_train.shape} | y_train: {y_train.shape}")
    print(f"  X_val:   {X_val.shape}  | y_val:   {y_val.shape}")
    print(f"  X_test:  {X_test.shape}  | y_test:  {y_test.shape}")

    # -------------------------------------------------------
    # 5. Build & train model
    # -------------------------------------------------------
    print("\n[7/8] Building LSTM model...")
    n_features = len(feature_cols)
    model = build_model(
        input_shape=(LOOKBACK, n_features),
        output_size=FORECAST_HORIZON,
        hidden_size=HIDDEN_SIZE,
        num_layers=NUM_LAYERS,
        dropout=DROPOUT,
    )
    model.summary()

    print(f"\n  Training ({EPOCHS} epochs)...")
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=7, restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=3
        ),
    ]

    start_time = time.time()
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        callbacks=callbacks,
        verbose=1,
    )
    train_time = time.time() - start_time
    print(f"  Training completed in {train_time:.1f}s")

    # -------------------------------------------------------
    # 6. Evaluate
    # -------------------------------------------------------
    print("\n[8/8] Evaluating on 2025 test data...")
    preds_scaled = model.predict(X_test, batch_size=BATCH_SIZE, verbose=0)

    preds = inverse_target(preds_scaled, scaler, n_features)
    targets = inverse_target(y_test, scaler, n_features)
    metrics = compute_metrics(preds, targets)

    print("\n" + "=" * 60)
    print("RESULTS")
    print("=" * 60)
    print(f"  Features used: {len(feature_cols)}")
    print(f"  Overall MAE:  {metrics['mae']:.2f} gCO2eq/kWh")
    print(f"  Overall RMSE: {metrics['rmse']:.2f} gCO2eq/kWh")
    print(f"  Overall MAPE: {metrics['mape']:.1f}%")
    print()
    print("  Per-horizon MAE (hours 1, 6, 12, 18, 24):")
    for h in [0, 5, 11, 17, 23]:
        if h < len(metrics["horizon_mae"]):
            print(f"    Hour {h+1:2d}: MAE={metrics['horizon_mae'][h]:.2f}, MAPE={metrics['horizon_mape'][h]:.1f}%")

    # --- Plots ---
    print("\n  Generating plots...")
    plot_results(history, preds, targets, metrics, feature_cols, RESULTS_DIR)

    # --- Save Keras model ---
    keras_path = os.path.join(RESULTS_DIR, "lstm_carbon_model.keras")
    model.save(keras_path)
    print(f"  Keras model saved: {keras_path}")

    # --- Convert to TFLite ---
    print("\n  Converting to TFLite...")
    tflite_path = os.path.join(RESULTS_DIR, "lstm_carbon_model.tflite")
    convert_to_tflite(model, tflite_path)
    test_tflite(tflite_path, X_test)

    # --- Save scaler params ---
    scaler_path = os.path.join(RESULTS_DIR, "scaler_params.npz")
    np.savez(scaler_path,
             data_min=scaler.data_min_,
             data_max=scaler.data_max_,
             feature_cols=feature_cols)
    print(f"  Scaler params saved: {scaler_path}")

    print("\n" + "=" * 60)
    print("Done! Check the 'results/' folder:")
    print(f"  - Keras model:  {keras_path}")
    print(f"  - TFLite model: {tflite_path}  <- use this on Android")
    print(f"  - Scaler:       {scaler_path}")
    print(f"  - Features:     {RESULTS_DIR}/features_used.txt")
    print(f"  - Plots:        {RESULTS_DIR}/*.png")
    print("=" * 60)


if __name__ == "__main__":
    main()