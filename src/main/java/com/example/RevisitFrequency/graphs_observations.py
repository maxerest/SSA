"""
plot_zones.py  –  Cumulative zone-pass chart from a CSV file.

Usage
-----
    python plot_zones.py data.csv               # auto-detect separator
    python plot_zones.py data.csv --sep ";"     # force semicolon separator
    python plot_zones.py data.csv --out result.png

Expected CSV columns (names are flexible, see --help):
    zone_name | start_time | end_time | name_sat_doing_observation
    (duration_s is ignored if present)
"""

import argparse
import sys
from pathlib import Path

import matplotlib.dates as mdates
import matplotlib.patches as mpatches
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


# ── CLI ───────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(
        description="Plot cumulative zone passes from a CSV file."
    )
    p.add_argument("csv", help="Path to the input CSV file")
    p.add_argument("--sep",       default=None,        help="Column separator (default: auto-detect ';' or ',')")
    p.add_argument("--zone-col",  default="zone_name", help="Zone column name (default: zone_name)")
    p.add_argument("--start-col", default="start_time",help="Start-time column name (default: start_time)")
    p.add_argument("--end-col",   default="end_time",  help="End-time column name (default: end_time)")
    p.add_argument("--sat-col",   default="name_sat_doing_observation",
                   help="Satellite column name (default: name_sat_doing_observation). Pass '' to disable.")
    p.add_argument("--out", default=None, help="Output image path (default: <csv_stem>_zones.png)")
    return p.parse_args()


# ── Load ──────────────────────────────────────────────────────────────────────

def load_csv(path, sep, start_col, end_col):
    csv_path = Path(path)
    if not csv_path.exists():
        sys.exit(f"Error: file not found → {path}")

    if sep is None:
        with open(csv_path, encoding="utf-8") as f:
            first_line = f.readline()
        sep = ";" if first_line.count(";") >= first_line.count(",") else ","
        print(f"Auto-detected separator: {sep!r}")

    df = pd.read_csv(csv_path, sep=sep)
    df[start_col] = pd.to_datetime(df[start_col], utc=True)
    df[end_col]   = pd.to_datetime(df[end_col],   utc=True)
    return df


# ── Plot ──────────────────────────────────────────────────────────────────────

# Distinct colours for up to ~12 satellites (cycles if more)
SAT_PALETTE = [
    "#58a6ff", "#f78166", "#3fb950", "#d2a8ff", "#ffa657",
    "#ff7b72", "#79c0ff", "#56d364", "#e3b341", "#bc8cff",
    "#ff9f88", "#70e1c8",
]

def sat_color_map(satellites):
    """Return {sat_name: hex_color} for every unique satellite."""
    unique = sorted(satellites.unique())
    return {s: SAT_PALETTE[i % len(SAT_PALETTE)] for i, s in enumerate(unique)}


def plot(df, zone_col, start_col, end_col, sat_col, out_path):
    global_start = df[start_col].min()
    global_end   = df[end_col].max()
    total_h      = (global_end - global_start).total_seconds() / 3600

    zones   = sorted(df[zone_col].unique())
    n_zones = len(zones)
    print(f"Found {n_zones} unique zone(s): {', '.join(zones)}")

    has_sat = sat_col and sat_col in df.columns
    if has_sat:
        cmap = sat_color_map(df[sat_col])
        print(f"Satellites: {list(cmap.keys())}")
    else:
        cmap = {}

    ncols = min(3, n_zones)
    nrows = int(np.ceil(n_zones / ncols))

    fig, axes = plt.subplots(nrows, ncols, figsize=(ncols * 5.4, nrows * 4.2))
    fig.patch.set_facecolor("#0d1117")
    axes_flat = np.array(axes).flatten()

    # Zone colour (for the step line when no sat info, or as fallback)
    zone_palette = plt.cm.tab10(np.linspace(0, 1, 10))

    for idx, zone in enumerate(zones):
        ax    = axes_flat[idx]
        ax.set_facecolor("#161b22")
        zone_color = zone_palette[idx % len(zone_palette)]

        zone_df     = df[df[zone_col] == zone].sort_values(start_col).reset_index(drop=True)
        event_times = zone_df[start_col].tolist()
        end_times   = zone_df[end_col].tolist()
        n_events    = len(event_times)

        # ── Step line (zone colour) ──────────────────────────────────────────
        xs = [global_start] + event_times + [global_end]
        ys = [0] + list(range(1, n_events + 1)) + [n_events]
        ax.step(xs, ys, where="post", color=zone_color, linewidth=1.8, zorder=3)
        ax.fill_between(xs, ys, step="post", color=zone_color, alpha=0.08, zorder=2)

        # ── Observation windows (horizontal spans, coloured by satellite) ────
        for i, row in zone_df.iterrows():
            sat  = row[sat_col] if has_sat else None
            col  = cmap.get(sat, "#ffffff") if has_sat else zone_color
            y_lo = i          # pass number before this event  (0-based index)
            y_hi = i + 1
            ax.axvspan(row[start_col], row[end_col],
                       ymin=(y_lo) / (n_events + 0.5),
                       ymax=(y_hi) / (n_events + 0.5),
                       color=col, alpha=0.28, zorder=1, linewidth=0)

            # Satellite label inside the span (only if wide enough)
            span_s = (row[end_col] - row[start_col]).total_seconds()
            total_s = (global_end - global_start).total_seconds()
            if has_sat and span_s / total_s > 0.03:
                mid_t = row[start_col] + (row[end_col] - row[start_col]) / 2
                ax.text(mid_t, i + 0.5, sat,
                        ha="center", va="center", fontsize=5.5,
                        color="white", alpha=0.85, zorder=4,
                        clip_on=True)

        # ── Axes formatting ──────────────────────────────────────────────────
        zone_window_min = (
                                  zone_df[end_col].max() - zone_df[start_col].min()
                          ).total_seconds() / 60

        ax.set_title(zone.replace("_", " "), color="white", fontsize=11,
                     fontweight="bold", pad=6)
        ax.set_xlabel("Time (UTC)", color="#8b949e", fontsize=8)
        ax.set_ylabel("Cumulative passes", color="#8b949e", fontsize=8)

        ax.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M"))
        ax.xaxis.set_major_locator(mdates.MinuteLocator(byminute=[0, 30]))
        plt.setp(ax.xaxis.get_majorticklabels(), rotation=30, ha="right",
                 fontsize=7, color="#8b949e")
        plt.setp(ax.yaxis.get_majorticklabels(), fontsize=8, color="#8b949e")

        ax.yaxis.set_major_locator(plt.MaxNLocator(integer=True))
        ax.set_xlim(global_start, global_end)
        ax.set_ylim(0, n_events + 0.5)

        for spine in ax.spines.values():
            spine.set_edgecolor("#30363d")
        ax.tick_params(colors="#8b949e", which="both")
        ax.grid(True, color="#21262d", linestyle="--", linewidth=0.5, zorder=0)

        ax.annotate(
            f"Window: {zone_window_min:.1f} min  |  Passes: {n_events}",
            xy=(0.03, 0.96), xycoords="axes fraction",
            fontsize=6.5, color="#58a6ff",
            bbox=dict(boxstyle="round,pad=0.3", fc="#0d1117", ec="#30363d", alpha=0.85),
            zorder=5,
        )

    # ── Hide unused subplots ─────────────────────────────────────────────────
    for j in range(n_zones, len(axes_flat)):
        axes_flat[j].set_visible(False)

    # ── Global title ─────────────────────────────────────────────────────────
    date_label = global_start.strftime("%Y-%m-%d")
    fig.suptitle(
        f"Cumulative Zone Passes  ·  {date_label}",
        color="white", fontsize=15, fontweight="bold", y=1.02,
    )
    fig.text(
        0.5, 1.005,
        f"Total observation window: {global_start.strftime('%H:%M')} – "
        f"{global_end.strftime('%H:%M')} UTC  ({total_h:.2f} h)",
        ha="center", va="top", fontsize=9, color="#8b949e",
    )

    # ── Satellite legend (bottom of figure) ──────────────────────────────────
    if has_sat and cmap:
        handles = [
            mpatches.Patch(color=col, label=sat, alpha=0.8)
            for sat, col in cmap.items()
        ]
        fig.legend(
            handles=handles, title="Satellite", title_fontsize=8,
            fontsize=8, loc="lower center",
            ncol=min(len(cmap), 6),
            frameon=True,
            facecolor="#161b22", edgecolor="#30363d",
            labelcolor="white",
            bbox_to_anchor=(0.5, -0.02),
        )

    plt.tight_layout(rect=[0, 0.03, 1, 1.0])
    plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor=fig.get_facecolor())
    print(f"Saved → {out_path}")
    plt.show()


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    args = parse_args()
    out_path = args.out or (Path(args.csv).stem + "_zones.png")
    df = load_csv(args.csv, args.sep, args.start_col, args.end_col)

    for col in (args.zone_col, args.start_col, args.end_col):
        if col not in df.columns:
            sys.exit(
                f"Error: column '{col}' not found.\n"
                f"Available columns: {list(df.columns)}\n"
                f"Use --zone-col / --start-col / --end-col to specify them."
            )

    sat_col = args.sat_col if args.sat_col else None
    if sat_col and sat_col not in df.columns:
        print(f"Warning: satellite column '{sat_col}' not found – satellite colouring disabled.")
        sat_col = None

    plot(df, args.zone_col, args.start_col, args.end_col, sat_col, out_path)
    plt.show()


if __name__ == "__main__":
    main()