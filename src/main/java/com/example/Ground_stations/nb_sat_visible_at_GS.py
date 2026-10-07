from pathlib import Path

import pandas as pd
import matplotlib.pyplot as plt


# ============================================================
# Load data
# ============================================================

path = Path("satcom_link.csv")


df = pd.read_csv(path)

# Convert timestamps
df["start_time"] = pd.to_datetime(df["start_time"], utc=True)
df["end_time"] = pd.to_datetime(df["end_time"], utc=True)

# Overall simulation time range
min_date = df["start_time"].min()
max_date = df["end_time"].max()

print("Simulation start:", min_date)
print("Simulation end:  ", max_date)


# ============================================================
# Extract masked angle and physical station name
#
# Example:
# 10_lat00_m180 -> angle = 10, station = lat00_m180
# 20_lat00_m180 -> angle = 20, station = lat00_m180
# 30_lat00_m180 -> angle = 30, station = lat00_m180
# ============================================================

df["masked_angle"] = (
    df["GS_name"]
    .str.extract(r"^(10|20|30)_")[0]
    .astype(float)
)

df["station"] = df["GS_name"].str.replace(
    r"^(10|20|30)_",
    "",
    regex=True
)

# Remove rows that do not follow the 10_/20_/30_ naming convention
df = df.dropna(subset=["masked_angle"])

df["masked_angle"] = df["masked_angle"].astype(int)


# ============================================================
# Function: number of simultaneous satellites over time
# ============================================================

def satellite_count(df_station_angle):

    # Satellite becomes visible: +1
    start_events = df_station_angle[["start_time"]].copy()
    start_events.columns = ["time"]
    start_events["change"] = 1

    # Satellite visibility ends: -1
    end_events = df_station_angle[["end_time"]].copy()
    end_events.columns = ["time"]
    end_events["change"] = -1

    # Combine start/end events
    events = pd.concat(
        [start_events, end_events],
        ignore_index=True
    )

    # If several events happen at exactly the same instant,
    # combine them
    events = (
        events.groupby("time")["change"]
        .sum()
        .sort_index()
    )

    # Add global start/end so every graph covers
    # the complete simulation period
    complete_index = events.index.union(
        pd.DatetimeIndex([min_date, max_date])
    ).sort_values()

    events = events.reindex(
        complete_index,
        fill_value=0
    )

    # Number of currently visible satellites
    counts = events.cumsum()

    return counts


# ============================================================
# Get all physical stations
# ============================================================

stations = sorted(df["station"].unique())

# ============================================================
# Generate ONE graph per physical station
# ============================================================

angles = [10, 20, 30]

for station in stations:

    df_station = df[df["station"] == station]

    plt.figure(figsize=(14, 6))

    largest_count = 0

    for angle in angles:

        df_angle = df_station[
            df_station["masked_angle"] == angle
            ]

        if df_angle.empty:
            print(f"No data for mask angle {angle}°")
            continue

        counts = satellite_count(df_angle)

        minimum = int(counts.min())
        maximum = int(counts.max())

        largest_count = max(
            largest_count,
            maximum
        )

        # Plot one line for this masked angle
        plt.step(
            counts.index,
            counts.values,
            where="post",
            label=f"{angle}° mask"
        )


    # ========================================================
    # Graph formatting
    # ========================================================

    plt.title(
        f"Simultaneously Visible Satellites\n"
        f"Ground Station: {station}"
    )

    plt.xlabel("Time")
    plt.ylabel("Number of visible satellites")

    plt.yticks(
        range(0, largest_count + 2)
    )

    plt.ylim(
        bottom=0
    )

    plt.xlim(
        min_date,
        max_date
    )

    plt.grid(
        True,
        alpha=0.3
    )

    plt.legend(
        title="Masked angle"
    )

    plt.tight_layout()

    plt.show()