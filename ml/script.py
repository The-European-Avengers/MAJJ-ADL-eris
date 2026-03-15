"""
Download ElectricityProdex5MinRealtime from Energi Data Service API.

How it works:
  1. Downloads data in chunks (30 days at a time)
  2. Saves each chunk as a CSV immediately (safe point)
  3. Tracks progress in progress.json — if it crashes, just re-run
     and it picks up where it left off
  4. Merges everything into one final CSV at the end

Usage:
    pip install requests pandas
    python download_electricity_prod.py

Output:
    electricity_prod_2021_2026.csv   — final merged file (~1.1M rows)
    chunks/                          — individual chunk CSVs (safe points)
    progress.json                    — deleted when download completes

Expected runtime: ~30-60 minutes (depends on your internet)
Expected size: ~200-300 MB final CSV
"""

import os
import json
import time
import requests
import pandas as pd
from datetime import datetime, timedelta

# ─── Configuration ───────────────────────────────────────────────
API_BASE    = "https://api.energidataservice.dk/dataset/ElectricityProdex5MinRealtime"
START_DATE  = datetime(2021, 1, 1)
END_DATE    = datetime(2026, 3, 7)       # adjust to today's date
CHUNK_DAYS  = 30                          # 1 month per request
PRICE_AREAS = ["DK1", "DK2"]             # both Danish bidding zones

# API note: limit=0 returns ALL rows for the given time range.
# With 30 days × 2 zones × 288 intervals/day = ~17,280 rows per chunk.
# This is well within what the API can handle.
REQUEST_LIMIT = 0                         # 0 = no limit (return all rows)

CHUNKS_DIR    = "chunks"
PROGRESS_FILE = "progress.json"
OUTPUT_FILE   = "electricity_prod_2021_2026.csv"

# How long to wait between API requests (seconds) — be polite
DELAY_BETWEEN_REQUESTS = 2
# How long to wait before retrying after an error
RETRY_DELAY = 30
MAX_RETRIES = 3

# ─── Helper functions ────────────────────────────────────────────

def load_progress():
    """Load progress from last run, or start fresh."""
    if os.path.exists(PROGRESS_FILE):
        with open(PROGRESS_FILE, "r") as f:
            prog = json.load(f)
        print(f"  ↪ Resuming from {prog['last_completed_end']}")
        return prog
    return {"last_completed_end": START_DATE.isoformat(), "chunks_downloaded": 0}


def save_progress(end_date_str, count):
    """Save progress so we can resume after crash."""
    prog = {"last_completed_end": end_date_str, "chunks_downloaded": count}
    with open(PROGRESS_FILE, "w") as f:
        json.dump(prog, f, indent=2)


def fetch_chunk(start, end):
    """
    Fetch one time chunk from the API.
    Note: start/end are interpreted as Danish local time by the API.
    Returns list of records and total count.
    """
    params = {
        "start":  start.strftime("%Y-%m-%dT%H:%M"),
        "end":    end.strftime("%Y-%m-%dT%H:%M"),
        "filter": json.dumps({"PriceArea": PRICE_AREAS}),
        "sort":   "Minutes5UTC asc",
        "limit":  REQUEST_LIMIT,
    }

    resp = requests.get(API_BASE, params=params, timeout=180)
    resp.raise_for_status()
    data = resp.json()

    records = data.get("records", [])
    total   = data.get("total", len(records))

    return records, total


def fetch_with_retries(start, end):
    """Fetch a chunk with automatic retries on failure."""
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return fetch_chunk(start, end)
        except requests.exceptions.RequestException as e:
            if attempt == MAX_RETRIES:
                print(f"\n  ❌ Failed after {MAX_RETRIES} attempts: {e}")
                print(f"  💾 Progress saved. Just re-run the script to continue.")
                raise
            print(f"\n  ⚠️  Attempt {attempt} failed: {e}")
            print(f"  ⏳ Waiting {RETRY_DELAY}s before retry {attempt+1}...")
            time.sleep(RETRY_DELAY)


# ─── Main download loop ─────────────────────────────────────────

def main():
    os.makedirs(CHUNKS_DIR, exist_ok=True)

    progress  = load_progress()
    current   = datetime.fromisoformat(progress["last_completed_end"])
    chunk_num = progress["chunks_downloaded"]

    # Calculate total chunks for progress display
    total_days   = (END_DATE - START_DATE).days
    total_chunks = (total_days + CHUNK_DAYS - 1) // CHUNK_DAYS

    print(f"╔══════════════════════════════════════════════════════════╗")
    print(f"║  Energi Data Service — ElectricityProdex5MinRealtime    ║")
    print(f"╠══════════════════════════════════════════════════════════╣")
    print(f"║  Period:  {START_DATE.date()} → {END_DATE.date()}                      ║")
    print(f"║  Zones:   DK1, DK2                                     ║")
    print(f"║  Chunks:  ~{total_chunks} chunks of {CHUNK_DAYS} days each               ║")
    print(f"╚══════════════════════════════════════════════════════════╝")
    print()

    total_rows = 0

    while current < END_DATE:
        chunk_end = min(current + timedelta(days=CHUNK_DAYS), END_DATE)
        chunk_file = os.path.join(CHUNKS_DIR, f"chunk_{chunk_num:04d}.csv")

        # Skip if this chunk was already downloaded
        if os.path.exists(chunk_file):
            existing = pd.read_csv(chunk_file)
            total_rows += len(existing)
            print(f"  [{chunk_num+1:3d}/{total_chunks}] "
                  f"{current.date()} → {chunk_end.date()}  "
                  f"— already saved ({len(existing):,} rows), skipping")
            current = chunk_end
            chunk_num += 1
            continue

        print(f"  [{chunk_num+1:3d}/{total_chunks}] "
              f"{current.date()} → {chunk_end.date()}  "
              f"— downloading...", end="", flush=True)

        try:
            records, api_total = fetch_with_retries(current, chunk_end)
        except requests.exceptions.RequestException:
            return  # Progress already saved in fetch_with_retries

        if records:
            df = pd.DataFrame(records)
            df.to_csv(chunk_file, index=False)
            total_rows += len(records)
            print(f"  ✅ {len(records):,} rows")
        else:
            # Save empty marker so we don't retry this chunk
            pd.DataFrame().to_csv(chunk_file, index=False)
            print(f"  ⚠️  No data for this period")

        # Save progress after each successful chunk
        current = chunk_end
        chunk_num += 1
        save_progress(current.isoformat(), chunk_num)

        # Be polite to the API
        time.sleep(DELAY_BETWEEN_REQUESTS)

    # ─── Merge all chunks into one CSV ───────────────────────────
    print()
    print("Merging all chunks into final CSV...")

    chunk_files = sorted(
        [os.path.join(CHUNKS_DIR, f) for f in os.listdir(CHUNKS_DIR)
         if f.endswith(".csv")]
    )

    dfs = []
    for cf in chunk_files:
        try:
            df = pd.read_csv(cf)
            if len(df) > 0:
                dfs.append(df)
        except Exception as e:
            print(f"  ⚠️  Could not read {cf}: {e}")

    if not dfs:
        print("No data downloaded!")
        return

    merged = pd.concat(dfs, ignore_index=True)

    # Drop duplicates (in case of overlapping chunk boundaries)
    before = len(merged)
    merged.drop_duplicates(subset=["Minutes5UTC", "PriceArea"], inplace=True)
    dupes = before - len(merged)
    if dupes > 0:
        print(f"  Removed {dupes:,} duplicate rows")

    merged.sort_values(["Minutes5UTC", "PriceArea"], inplace=True)
    merged.reset_index(drop=True, inplace=True)

    merged.to_csv(OUTPUT_FILE, index=False)

    # Print summary
    print()
    print(f"╔══════════════════════════════════════════════════════════╗")
    print(f"║  ✅ Download complete!                                  ║")
    print(f"╠══════════════════════════════════════════════════════════╣")
    print(f"║  File:    {OUTPUT_FILE:<46s} ║")
    print(f"║  Rows:    {len(merged):>10,}                                  ║")
    print(f"║  Period:  {merged['Minutes5UTC'].min()[:10]} → {merged['Minutes5UTC'].max()[:10]}                ║")
    print(f"║  DK1:     {(merged['PriceArea']=='DK1').sum():>10,} rows                          ║")
    print(f"║  DK2:     {(merged['PriceArea']=='DK2').sum():>10,} rows                          ║")
    print(f"╚══════════════════════════════════════════════════════════╝")
    print()
    print(f"  Columns: {list(merged.columns)}")
    print()

    # Clean up progress file since we're done
    if os.path.exists(PROGRESS_FILE):
        os.remove(PROGRESS_FILE)
        print("  🧹 Removed progress.json (download complete)")
    print()
    print("  You can now delete the chunks/ folder if you want.")
    print("  The full dataset is in:", OUTPUT_FILE)


if __name__ == "__main__":
    main()