"""data_loader.py — Smart data loading for LSTM models with caching"""
import glob
import os

import pandas as pd


class DataLoader:
    """Efficient, reusable data loader with intelligent caching & merging."""

    def __init__(self, base_paths=None):
        """
        Args:
            base_paths: dict with 'emissions', 'weather', 'prices' paths
                       Defaults to relative paths: ../emissions-data, ../weather, ../prices
        """
        if base_paths is None:
            base_paths = {
                'emissions': "../emissions-data",
                'weather': "../weather",
                'prices': "../prices"
            }
        self.paths = base_paths
        self._cache = {}

    def load_emissions(self):
        """Load all emissions CSVs, return sorted DataFrame."""
        cache_key = "emissions_full"
        if cache_key in self._cache:
            print(f"(cached) emissions_full")
            return self._cache[cache_key].copy()

        csv_files = sorted(glob.glob(os.path.join(self.paths['emissions'], "*.csv")))
        dfs = [pd.read_csv(f, parse_dates=["Datetime (UTC)"]) for f in csv_files]
        df = pd.concat(dfs, ignore_index=True)
        df = df.sort_values("Datetime (UTC)").reset_index(drop=True)

        self._cache[cache_key] = df
        print(f"Emissions: {len(df)} rows, {df['Datetime (UTC)'].min()} to {df['Datetime (UTC)'].max()}")
        print(f"Columns: {list(df.columns)}")
        return df.copy()

    def load_weather(self):
        """Load wind speed & temperature from DMI CSVs, return dict of DataFrames."""
        cache_key = "weather"
        if cache_key in self._cache:
            print(f"(cached) {cache_key}")
            return {k: v.copy() for k, v in self._cache[cache_key].items()}

        print("Loading DMI weather data...")
        wind_df = self._load_dmi_data("*_dmi_wind*", "mean_wind_speed", "wind_speed")
        temp_df = self._load_dmi_data("*_dmi_temp*", "mean_temp", "temperature")

        weather = {'wind_speed': wind_df, 'temperature': temp_df}
        self._cache[cache_key] = weather
        return {k: v.copy() for k, v in weather.items()}

    def _load_dmi_data(self, pattern, value_col, new_name):
        """Helper: Load DMI weather CSVs, aggregate to hourly."""
        files = sorted(glob.glob(os.path.join(self.paths['weather'], pattern)))
        if not files:
            print(f"No files for {new_name}")
            return pd.DataFrame()

        dfs = []
        for f in files:
            try:
                dfs.append(pd.read_csv(f))
            except Exception as e:
                print(f"Could not read {f}: {e}")

        if not dfs:
            return pd.DataFrame()

        weather = pd.concat(dfs, ignore_index=True)
        weather["timeObserved"] = pd.to_datetime(weather["timeObserved"], format="ISO8601", utc=True)
        weather["timeObserved"] = weather["timeObserved"].dt.tz_localize(None)
        weather["hour_utc"] = weather["timeObserved"].dt.floor("h")

        hourly = weather.groupby("hour_utc")[value_col].median().reset_index()
        hourly.columns = ["Datetime (UTC)", new_name]

        print(f"  {new_name}: {len(hourly)} hours, {weather['stationId'].nunique()} stations")
        return hourly

    def load_prices(self):
        """Load day-ahead prices (handles both 2021-2024 & 2025 formats)."""
        cache_key = "prices"
        if cache_key in self._cache:
            print(f"(cached) {cache_key}")
            return self._cache[cache_key].copy()

        print("Loading electricity prices...")

        # Old format (2021-2024)
        old_price_files = sorted(glob.glob(os.path.join(self.paths['prices'], "DayAheadPrices_DK1_*.csv")))
        old_prices = self._load_old_prices(old_price_files)

        # New format (2025)
        new_price_files = sorted(glob.glob(os.path.join(self.paths['prices'], "DK*-DayAhead_Prices-*.csv")))
        new_prices = self._load_new_prices(new_price_files)

        # Combine
        prices = pd.concat([old_prices, new_prices], ignore_index=True)
        prices = prices.drop_duplicates(subset="Datetime (UTC)").sort_values("Datetime (UTC)")
        prices = prices.reset_index(drop=True)

        self._cache[cache_key] = prices
        print(f"Combined: {len(prices)} rows, {prices['Datetime (UTC)'].min()} to {prices['Datetime (UTC)'].max()}")
        return prices.copy()

    def _load_old_prices(self, files):
        """Helper: Load 2021-2024 hourly prices."""
        price_dfs = []
        for f in files:
            try:
                price_dfs.append(pd.read_csv(f))
            except Exception as e:
                print(f"{f}: {e}")

        if not price_dfs:
            return pd.DataFrame(columns=["Datetime (UTC)", "price_eur_mwh"])

        prices = pd.concat(price_dfs, ignore_index=True)
        prices["Datetime (UTC)"] = pd.to_datetime(
            prices["MTU (UTC)"].str.split(" - ").str[0],
            format="%d/%m/%Y %H:%M:%S"
        )
        prices["price_eur_mwh"] = pd.to_numeric(prices["Day-ahead Price (EUR/MWh)"], errors="coerce")
        prices = prices[["Datetime (UTC)", "price_eur_mwh"]].dropna()

        print(f"  Old format: {len(prices)} rows")
        return prices

    def _load_new_prices(self, files):
        """Helper: Load 2025 15-min prices, resample to hourly."""
        price_dfs = []
        for f in files:
            try:
                price_dfs.append(pd.read_csv(f))
            except Exception as e:
                print(f"{f}: {e}")

        if not price_dfs:
            return pd.DataFrame(columns=["Datetime (UTC)", "price_eur_mwh"])

        prices = pd.concat(price_dfs, ignore_index=True)
        prices["Datetime (UTC)"] = pd.to_datetime(
            prices["MTU (UTC)"].str.split(" - ").str[0],
            format="%d/%m/%Y %H:%M:%S"
        )
        prices["price_eur_mwh"] = pd.to_numeric(prices["Day-ahead Price (EUR/MWh)"], errors="coerce")
        prices = prices[["Datetime (UTC)", "price_eur_mwh"]].dropna()

        # Resample 15-min → hourly
        prices = prices.set_index("Datetime (UTC)").resample("1h").mean().reset_index()

        print(f"  New format (resampled): {len(prices)} rows")
        return prices

    def merge_all(self):
        """Load and merge all data in one call."""
        print("\n=== Loading Complete Dataset ===")

        merged = self.load_emissions()

        weather = self.load_weather()
        for weather_df in [weather.get('wind_speed'), weather.get('temperature')]:
            if weather_df is not None and len(weather_df) > 0:
                merged = merged.merge(weather_df, on="Datetime (UTC)", how="left")

        prices = self.load_prices()
        merged = merged.merge(prices, on="Datetime (UTC)", how="left")

        print(f"\nMerged: {len(merged)} rows before feature engineering")
        return merged
