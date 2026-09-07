import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import tkinter as tk

from tkinter import ttk
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg


# --------------------------------------------------
# Load data
# --------------------------------------------------

df = pd.read_csv(
    "../../../../../../src/main/resources/EO detection/observations.csv"
)

df["start_time"] = pd.to_datetime(df["start_time"])
df["end_time"] = pd.to_datetime(df["end_time"])


# --------------------------------------------------
# Get unique zones
# --------------------------------------------------

zones = sorted(df["zone_name"].dropna().unique())


# --------------------------------------------------
# Create GUI
# --------------------------------------------------

root = tk.Tk()
root.title("EO Observation Viewer")
root.state("zoomed")



# --------------------------------------------------
# Top control panel
# --------------------------------------------------

control_frame = ttk.Frame(root)
control_frame.pack(fill="x", padx=10, pady=10)


# Zone label
ttk.Label(
    control_frame,
    text="Zone:"
).pack(side="left", padx=(0, 5))


# Zone dropdown
zone_var = tk.StringVar()

zone_dropdown = ttk.Combobox(
    control_frame,
    textvariable=zone_var,
    values=zones,
    state="readonly",
    width=40
)

zone_dropdown.pack(
    side="left",
    padx=(0, 30)
)


# Pass count label
ttk.Label(
    control_frame,
    text="Number of passes:"
).pack(side="left", padx=(0, 5))


# Pass count
pass_var = tk.StringVar()

pass_entry = ttk.Entry(
    control_frame,
    textvariable=pass_var,
    state="readonly",
    width=10
)

pass_entry.pack(side="left", padx=(0, 30))


# Total observation time label
ttk.Label(
    control_frame,
    text="Total observation time:"
).pack(side="left", padx=(0, 5))


# Total observation time value
total_time_var = tk.StringVar()

total_time_entry = ttk.Entry(
    control_frame,
    textvariable=total_time_var,
    state="readonly",
    width=18
)

total_time_entry.pack(side="left")
# Minimum gap between passes
ttk.Label(
    control_frame,
    text="Min gap:"
).pack(side="left", padx=(20, 5))

min_gap_var = tk.StringVar()

min_gap_entry = ttk.Entry(
    control_frame,
    textvariable=min_gap_var,
    state="readonly",
    width=12
)

min_gap_entry.pack(side="left")


# Maximum gap between passes
ttk.Label(
    control_frame,
    text="Max gap:"
).pack(side="left", padx=(20, 5))

max_gap_var = tk.StringVar()

max_gap_entry = ttk.Entry(
    control_frame,
    textvariable=max_gap_var,
    state="readonly",
    width=12
)

max_gap_entry.pack(side="left")

# --------------------------------------------------
# Create Matplotlib figure
# --------------------------------------------------

fig = plt.Figure(figsize=(14, 8))

canvas = FigureCanvasTkAgg(
    fig,
    master=root
)

canvas.get_tk_widget().pack(
    fill="both",
    expand=True
)


# --------------------------------------------------
# Plot selected zone
# --------------------------------------------------

def plot_zone(event=None):

    zone = zone_var.get()

    if not zone:
        return

    zone_df = df[
        df["zone_name"] == zone
        ].copy()

    # --------------------------------------------------
    # Common X-axis range
    # --------------------------------------------------

    global_min_time = df["start_time"].min()
    global_max_time = df["end_time"].max()

    # Number of passes
    number_of_passes = len(zone_df)
    pass_var.set(str(number_of_passes))

    # Total duration
    total_duration = (
            zone_df["end_time"] - zone_df["start_time"]
    ).sum()

    total_seconds = int(total_duration.total_seconds())

    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    seconds = total_seconds % 60

    total_time_var.set(
        f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    )

    sats = sorted(
        zone_df["name_sat_doing_observation"]
        .dropna()
        .unique()
    )

    fig.clear()

    if len(sats) == 0:
        ax = fig.add_subplot(111)

        ax.text(
            0.5,
            0.5,
            "No observations available",
            ha="center",
            va="center",
            fontsize=16
        )

        ax.axis("off")
        canvas.draw_idle()
        return
    # --------------------------------------------------
    # Minimum / Maximum time between passes
    # --------------------------------------------------

    passes_sorted = zone_df.sort_values("start_time").reset_index(drop=True)

    gaps_minutes = []

    for i in range(1, len(passes_sorted)):

        previous_end = passes_sorted.loc[i - 1, "end_time"]
        current_start = passes_sorted.loc[i, "start_time"]

        gap = (
                      current_start - previous_end
              ).total_seconds() / 60.0

        # Only count actual gaps.
        # Ignore overlapping passes.
        if gap >= 0:
            gaps_minutes.append(gap)


    if gaps_minutes:

        min_gap = min(gaps_minutes)
        max_gap = max(gaps_minutes)

        min_gap_var.set(f"{min_gap:.1f} min")
        max_gap_var.set(f"{max_gap:.1f} min")

    else:

        min_gap_var.set("N/A")
        max_gap_var.set("N/A")
    # --------------------------------------------------
    # Two graphs sharing the SAME X axis
    # --------------------------------------------------

    ax_passes, ax_cum = fig.subplots(
        2,
        1,
        sharex=True,
        gridspec_kw={
            "height_ratios": [3, 2]
        }
    )

    colors = plt.cm.tab10.colors

    # --------------------------------------------------
    # Satellite passes
    # --------------------------------------------------

    for i, sat in enumerate(sats):

        sat_df = zone_df[
            zone_df["name_sat_doing_observation"] == sat
            ].sort_values("start_time")

        color = colors[i % len(colors)]

        for _, row in sat_df.iterrows():

            ax_passes.barh(
                y=i,
                width=row["end_time"] - row["start_time"],
                left=row["start_time"],
                height=0.6,
                color=color,
                alpha=0.8
            )

    ax_passes.set_yticks(range(len(sats)))
    ax_passes.set_yticklabels(sats)

    ax_passes.set_ylabel("Satellite")
    ax_passes.set_title("Satellite observation passes")

    ax_passes.grid(
        axis="x",
        linestyle="--",
        alpha=0.4
    )

    ax_passes.invert_yaxis()

    # --------------------------------------------------
    # Cumulative graph
    # --------------------------------------------------

    all_df = zone_df.sort_values("start_time")

    times_all = [global_min_time]
    counts_all = [0]

    for _, row in all_df.iterrows():

        times_all.append(row["start_time"])
        counts_all.append(counts_all[-1] + 1)

    times_all.append(global_max_time)
    counts_all.append(counts_all[-1])

    ax_cum.step(
        times_all,
        counts_all,
        where="post",
        color="black",
        linewidth=2,
        label="All Satellites"
    )

    ax_cum.set_ylabel("Cumulative Obs.")
    ax_cum.set_xlabel("Time (UTC)")

    ax_cum.legend(
        loc="upper left"
    )

    ax_cum.grid(
        linestyle="--",
        alpha=0.4
    )

    # --------------------------------------------------
    # FORCE EXACTLY THE SAME TIME RANGE
    # --------------------------------------------------

    ax_passes.set_xlim(
        global_min_time,
        global_max_time
    )

    ax_cum.set_xlim(
        global_min_time,
        global_max_time
    )

    # --------------------------------------------------
    # Title
    # --------------------------------------------------

    fig.suptitle(
        f"Observations over {zone}",
        fontsize=14,
        fontweight="bold"
    )

    fig.autofmt_xdate()

    fig.tight_layout(
        rect=[0, 0, 1, 0.96]
    )

    canvas.draw_idle()


# --------------------------------------------------
# Dropdown event
# --------------------------------------------------

zone_dropdown.bind(
    "<<ComboboxSelected>>",
    plot_zone
)


# --------------------------------------------------
# Select first zone automatically
# --------------------------------------------------

if zones:

    zone_dropdown.current(0)

    plot_zone()


# --------------------------------------------------
# Start application
# --------------------------------------------------

root.mainloop()