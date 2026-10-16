import re
from datetime import datetime, timedelta

import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.ticker import PercentFormatter


# ===D=========================================================
# CONFIGURATION
# ============================================================

CSV_FILE = "../../../../resources/Satcom/satcom_link.csv"

SIMULATION_START = "2026-10-07T11:37:00Z"
SIMULATION_DURATION_S = 777600

# Elevation masks to compare
ELEVATION_MASKS = [10, 20, 30]

# Ground stations every 3 degrees
LATITUDES = range(0, 91, 3)


# ============================================================
# DATE PARSING
# ============================================================

def parse_orekit_date(date_string):
    """
    Orekit may output timestamps with more fractional-second
    digits than Python datetime accepts.

    Example:
    2026-10-07T12:01:47.745033309141945216Z

    becomes:
    2026-10-07T12:01:47.745033Z
    """

    date_string = str(date_string).strip()

    date_string = re.sub(
        r"(\.\d{6})\d*Z$",
        r"\1Z",
        date_string
    )

    return datetime.fromisoformat(
        date_string.replace("Z", "+00:00")
    )


# ============================================================
# MERGE OVERLAPPING VISIBILITY INTERVALS
# To know if there are at least 2 sats visible, we need to look at the intercept of revisit
# ============================================================

def merge_intervals(intervals, minimum_satellites=2):

    if not intervals:
        return []

    events = []

    # Each visibility interval creates:
    # +1 satellite at start
    # -1 satellite at end
    for start, end in intervals:

        if end <= start:
            continue

        events.append((start, +1))
        events.append((end, -1))

    if not events:
        return []

    # Sort chronologically
    events.sort(key=lambda x: x[0])

    merged = []

    active_satellites = 0
    interval_start = None

    i = 0

    while i < len(events):

        time = events[i][0]

        # Several events can happen at exactly the same time
        delta = 0

        while (
                i < len(events)
                and events[i][0] == time
        ):
            delta += events[i][1]
            i += 1

        previous_count = active_satellites
        active_satellites += delta

        # We just reached the required number
        if (
                previous_count < minimum_satellites <= active_satellites
        ):
            interval_start = time

        # We just dropped below the required number
        elif (
                previous_count >= minimum_satellites > active_satellites
        ):

            if (
                    interval_start is not None
                    and time > interval_start
            ):
                merged.append(
                    [interval_start, time]
                )

            interval_start = None

    return merged

# ============================================================
# SIMULATION PERIOD
# ============================================================

simulation_start = parse_orekit_date(
    SIMULATION_START
)

simulation_end = (
        simulation_start
        + timedelta(seconds=SIMULATION_DURATION_S)
)

simulation_duration = SIMULATION_DURATION_S


print("Simulation start :", simulation_start)
print("Simulation end   :", simulation_end)
print("Duration         :", simulation_duration, "s")


# ============================================================
# READ CSV
# ============================================================

df = pd.read_csv(CSV_FILE)


# Parse Orekit timestamps
df["start"] = (
    df["start_time"]
    .apply(parse_orekit_date)
)

df["end"] = (
    df["end_time"]
    .apply(parse_orekit_date)
)


# ============================================================
# EXTRACT LATITUDE AND MASK ANGLE FROM GS NAME
# ============================================================

# Examples:
#
# 10_lat45
# 20_lat45
# 30_lat45

df["latitude"] = (
    df["GS_name"]
    .str.extract(
        r"lat(-?\d+(?:\.\d+)?)"
    )[0]
    .astype(float)
)

df["mask_angle"] = (
    df["GS_name"]
    .str.extract(
        r"^(\d+(?:\.\d+)?)_"
    )[0]
    .astype(float)
)


# ============================================================
# CALCULATE AVAILABILITY
# ============================================================

results = []


for gs_name, group in df.groupby("GS_name"):

    latitude = group["latitude"].iloc[0]
    mask_angle = group["mask_angle"].iloc[0]

    intervals = []

    for _, row in group.iterrows():

        # Clamp to simulation interval
        start = max(
            row["start"],
            simulation_start
        )

        end = min(
            row["end"],
            simulation_end
        )

        if end > start:
            intervals.append(
                (start, end)
            )


    # Merge all satellite visibility intervals
    merged = merge_intervals(
        intervals,
        minimum_satellites=2
    )

    # Total duration where >= 1 satellite is visible
    visible_seconds = sum(
        (end - start).total_seconds()
        for start, end in merged
    )


    availability_percent = (
            100.0
            * visible_seconds
            / simulation_duration
    )


    results.append({
        "GS_name": gs_name,
        "latitude": latitude,
        "mask_angle": mask_angle,
        "visible_seconds": visible_seconds,
        "availability_percent": availability_percent
    })


results_df = pd.DataFrame(results)


# ============================================================
# ADD REAL GS THAT HAD ZERO VISIBILITY
# ============================================================

# satcom_link.csv only contains visibility events.
# If a real GS sees no satellite at all,
# it will not appear in the file.
#
# We therefore create only the actual grid:
# latitude = 0, 3, 6, ..., 90
# masks = 10, 20, 30

expected = []


for mask_angle in ELEVATION_MASKS:

    for latitude in LATITUDES:

        expected.append({
            "latitude": float(latitude),
            "mask_angle": float(mask_angle)
        })


expected_df = pd.DataFrame(expected)


results_df = expected_df.merge(
    results_df,
    on=[
        "latitude",
        "mask_angle"
    ],
    how="left"
)


# Missing = GS existed but had zero visibility
results_df["visible_seconds"] = (
    results_df["visible_seconds"]
    .fillna(0.0)
)

results_df["availability_percent"] = (
    results_df["availability_percent"]
    .fillna(0.0)
)


# ============================================================
# SORT RESULTS
# ============================================================

results_df = results_df.sort_values(
    [
        "mask_angle",
        "latitude"
    ]
)




# ============================================================
# EXPORT RESULTS
# ============================================================

OUTPUT_FILE = "visibility_availability_by_latitude.csv"

results_df.to_csv(
    OUTPUT_FILE,
    index=False
)

print()
print("Results saved to:", OUTPUT_FILE)


# ============================================================
# PRETTY PLOT
# ============================================================

fig, ax = plt.subplots(
    figsize=(11, 6.5)
)


# Different marker for each mask
markers = {
    10: "o",
    20: "s",
    30: "^"
}


for mask_angle in ELEVATION_MASKS:

    group = results_df[
        results_df["mask_angle"] == mask_angle
        ].sort_values(
        "latitude"
    )

    ax.plot(
        group["latitude"],
        group["availability_percent"],

        marker=markers.get(
            mask_angle,
            "o"
        ),

        # Marker every second point
        markevery=2,

        markersize=5,

        linewidth=2.2,

        label=f"{mask_angle}° elevation mask"
    )


# ============================================================
# AXES LABELS
# ============================================================

ax.set_xlabel(
    "Observer latitude [°]",
    fontsize=12
)

ax.set_ylabel(
    "Satellite visibility availability",
    fontsize=12
)


# ============================================================
# TITLE
# ============================================================

ax.set_title(
    "Satellite Visibility Availability vs Latitude for FSA-1",
    fontsize=15,
    fontweight="bold",
    pad=15
)


# ============================================================
# LIMITS
# ============================================================

ax.set_xlim(
    0,
    90
)

ax.set_ylim(
    0,
    105
)


# ============================================================
# TICKS
# ============================================================

ax.set_xticks(
    range(0, 91, 10)
)

ax.set_yticks(
    range(0, 101, 10)
)


# Show y-axis as percentages
ax.yaxis.set_major_formatter(
    PercentFormatter(xmax=100)
)


# ============================================================
# GRID
# ============================================================

ax.grid(
    axis="y",
    linestyle="--",
    linewidth=0.7,
    alpha=0.35
)

ax.grid(
    axis="x",
    linestyle=":",
    linewidth=0.5,
    alpha=0.2
)


# ============================================================
# LEGEND
# ============================================================

ax.legend(
    title="Elevation mask",
    frameon=True,
    fontsize=10,
    title_fontsize=10,
    loc="best"
)


# ============================================================
# CLEAN FRAME
# ============================================================

ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)

ax.tick_params(
    axis="both",
    labelsize=10
)


# ============================================================
# LAYOUT
# ============================================================

fig.tight_layout()


# ============================================================
# SAVE HIGH-RESOLUTION IMAGE
# ============================================================

fig.savefig(
    "satellite_visibility_vs_latitude.png",
    dpi=300,
    bbox_inches="tight"
)


# ============================================================
# SHOW
# ============================================================

plt.show()