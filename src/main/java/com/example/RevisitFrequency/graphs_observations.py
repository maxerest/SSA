import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

df = pd.read_csv("src/main/resources/EO detection/observations.csv")

df["start_time"] = pd.to_datetime(df["start_time"])
df["end_time"] = pd.to_datetime(df["end_time"])

zones = df["zone_name"].unique()

for zone in zones:
    zone_df = df[df["zone_name"] == zone]
    sats = sorted(zone_df["name_sat_doing_observation"].unique())

    # Total rows: one per sat + one for cumulative
    n_sats = len(sats)
    fig, axes = plt.subplots(
        n_sats + 1, 1,
        figsize=(14, 3 + n_sats * 1.5),
        sharex=True,
        gridspec_kw={"height_ratios": [1] * n_sats + [2]}
    )

    colors = plt.cm.tab10.colors

    for i, sat in enumerate(sats):
        ax = axes[i]
        sat_df = zone_df[zone_df["name_sat_doing_observation"] == sat].sort_values("start_time")
        color = colors[i % len(colors)]

        for _, row in sat_df.iterrows():
            ax.axvspan(row["start_time"], row["end_time"], color=color, alpha=0.8)

        ax.set_yticks([0.5])
        ax.set_yticklabels([sat], fontsize=9)
        ax.set_ylim(0, 1)
        ax.set_ylabel("")
        ax.grid(axis="x", linestyle="--", alpha=0.4)

    # Cumulative line on the last axis
    ax_cum = axes[-1]
    all_df = zone_df.sort_values("start_time")
    times_all = [all_df["start_time"].min()]
    counts_all = [0]

    for _, row in all_df.iterrows():
        times_all.append(row["start_time"])
        counts_all.append(counts_all[-1] + 1)

    times_all.append(all_df["end_time"].max())
    counts_all.append(counts_all[-1])

    ax_cum.step(times_all, counts_all, where="post", color="black", linewidth=2, label="All Satellites")
    ax_cum.set_ylabel("Cumulative Obs.")
    ax_cum.set_xlabel("Time (UTC)")
    ax_cum.legend(loc="upper left")
    ax_cum.grid(linestyle="--", alpha=0.4)

    plt.suptitle(f"Observations over {zone}", fontsize=13, fontweight="bold")
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()

print("Done!")