import pyvista as pv
from pyvista import examples
import pandas as pd
import numpy as np
import time
import os
import sys

# ---------------------------------------------------------------------------
# Initial Earth angle from Java (GAST at epoch) — argv[1]
# ---------------------------------------------------------------------------
initial_earth_angle = float(sys.argv[1]) if len(sys.argv) > 1 else 180.0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def latlon_to_ecef(lat_deg, lon_deg, alt_km=0, R=6378100):
    """Convert latitude/longitude to ECEF coordinates (no rotation applied here)"""
    lat = np.radians(lat_deg)
    lon = np.radians(lon_deg)
    r   = R + alt_km
    x   = r * np.cos(lat) * np.cos(lon)
    y   = r * np.cos(lat) * np.sin(lon)
    z   = r * np.sin(lat)
    return np.array([x, y, z])

def build_cone_points(base_center, half_angle_deg=45.0, cone_height_m=2_000_000.0, resolution=60):
    """
    Returns (points, faces) for a cone with apex on Earth surface, opening outward.
    Points are in raw ECEF — rotation is applied each frame via rotation_matrix_z.
    """
    base_center = np.array(base_center, dtype=float)
    outward     = base_center / np.linalg.norm(base_center)
    top         = base_center + outward * cone_height_m
    radius      = cone_height_m * np.tan(np.radians(half_angle_deg))

    arbitrary = np.array([0, 0, 1]) if abs(outward[2]) < 0.9 else np.array([1, 0, 0])
    u = np.cross(outward, arbitrary);  u /= np.linalg.norm(u)
    v = np.cross(outward, u)

    angles = np.linspace(0, 2 * np.pi, resolution, endpoint=False)
    circle = np.array([top + radius * (np.cos(a) * u + np.sin(a) * v) for a in angles])

    points = np.vstack([base_center, circle])   # index 0 = apex = station position
    n      = len(circle)
    faces  = []
    for i in range(n):
        next_i = (i + 1) % n
        faces += [3, 0, next_i + 1, i + 1]

    return points, np.array(faces)

def rotation_matrix_z(angle_deg):
    """3x3 rotation matrix around Z axis."""
    a   = np.radians(angle_deg)
    cos = np.cos(a)
    sin = np.sin(a)
    return np.array([
        [ cos, -sin, 0],
        [ sin,  cos, 0],
        [   0,    0, 1]
    ])

def get_time_based_frames(satellites, frame_interval=60.0):
    all_times = []
    for sat in satellites:
        all_times.extend(sat['points'][:, 3])
    if len(all_times) == 0:
        return []
    t_min = min(all_times)
    t_max = max(all_times)
    frame_boundaries = np.arange(t_min, t_max + frame_interval, frame_interval)
    frames = []
    for frame_start, frame_end in zip(frame_boundaries[:-1], frame_boundaries[1:]):
        frame_indices = []
        for sat in satellites:
            mask    = (sat['points'][:, 3] >= frame_start) & (sat['points'][:, 3] < frame_end)
            indices = np.where(mask)[0]
            frame_indices.append(indices)
        frames.append((frame_start, frame_end, frame_indices))
    return frames

def select_csv_file():
    file_path = "src/main/java/com/example/View/real_sats.csv"
    if os.path.exists(file_path):
        return file_path
    return "src/main/java/com/example/View/TLE.csv"

def select_gs_csv_file():
    return "src/main/java/com/example/Ground_stations/GS_coordinates.csv"

# ---------------------------------------------------------------------------
# Initialize
# ---------------------------------------------------------------------------
pv.close_all()

sat_file = select_csv_file()
if not sat_file:
    print("No satellite file selected!")
    exit()
print(f"Loading satellite data from: {sat_file}")

plotter = pv.Plotter()
plotter.enable_depth_peeling(number_of_peels=4)

texture = examples.load_globe_texture()
Earth   = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000, 1000, 1000], inplace=True)
Earth.rotate_z(0, inplace=True)
plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# ---------------------------------------------------------------------------
# Ground stations + cones
# All stored in raw ECEF — a single R_mat handles ALL rotation each frame
# including the initial_earth_angle offset, so there is zero drift possible
# listGS: (name, gs_sphere, cone_mesh, orig_pts, orig_center, activation)
# ---------------------------------------------------------------------------
listGS               = []
total_earth_rotation = 0.0   # cumulative rotation AFTER the initial angle

try:
    gs_file = select_gs_csv_file()
    if gs_file and os.path.exists(gs_file):
        GS_csv = pd.read_csv(gs_file)

        for idx, row in GS_csv.iterrows():
            name       = row["name"]      if "name"      in row else f"GS_{idx}"
            lat        = float(row["lat"])
            lon        = float(row["long"]) if "long" in row else float(row["lon"])
            alt        = float(row["alt"]) if "alt" in row else 0
            activation = bool(row["activated"]) if "activated" in row else True

            # Raw ECEF — no rotation applied here
            gs_pos   = latlon_to_ecef(lat, lon, alt)
            gs_color = "green" if activation else "red"

            # Build cone in raw ECEF
            orig_pts, faces = build_cone_points(
                base_center    = gs_pos,
                half_angle_deg = 75.0,
                cone_height_m  = 2_000_000.0,
                resolution     = 60
            )
            orig_center = orig_pts[0].copy()   # index 0 = apex = station position
            R_init = rotation_matrix_z(180)
            init_center = R_init @ orig_center
            init_pts    = (R_init @ orig_pts.T).T


            # Ground station sphere — placed at initial rotated position
            gs_sphere = pv.Sphere(radius=1.5e5, center=init_center)
            plotter.add_mesh(gs_sphere, color=gs_color, smooth_shading=True, name=f"gs_{name}")

            cone_mesh = pv.PolyData(init_pts.copy(), faces)
            plotter.add_mesh(cone_mesh, color=gs_color, opacity=0.25,
                             smooth_shading=False, show_edges=False, name=f"cone_{name}")


            # Cone mesh — placed at initial rotated position
            cone_mesh = pv.PolyData(orig_pts.copy(), faces)
            plotter.add_mesh(
                cone_mesh,
                color          = gs_color,
                opacity        = 0.25,
                smooth_shading = False,
                show_edges     = False,
                name           = f"cone_{name}",
            )

            # Store raw ECEF originals — the animation loop applies total rotation
            listGS.append((name, gs_sphere, cone_mesh, orig_pts, orig_center, activation))
            print(f"Added ground station: {name}")

except Exception as e:
    print(f"Could not load ground stations: {e}")

# ---------------------------------------------------------------------------
# Satellites
# ---------------------------------------------------------------------------
df        = pd.read_csv(sat_file)
sat_names = df['name_sat'].unique()
print(f"Found {len(sat_names)} satellites: {sat_names}")

base_colors = {
    'Sat_real_1': [1.0, 0.0, 0.0],
    'Sat_real_2': [0.0, 0.0, 1.0],
    'Sat_real_3': [0.0, 1.0, 0.0],
    'Sat_real_4': [1.0, 1.0, 0.0],
    'Sat_real_5': [1.0, 0.0, 1.0],
    'Sat_real_6': [0.0, 1.0, 1.0],
}

trail_length = 1
satellites   = []

for sat_name in sat_names:
    if not isinstance(sat_name, str):
        continue

    sat_data   = df[df['name_sat'] == sat_name]
    is_noisy   = 'noisy' in sat_name.lower()
    base_color = base_colors.get(sat_name, [0.5, 0.5, 0.5])

    if "detected_by_GS" in sat_data.columns:
        detected_col = (
            sat_data["detected_by_GS"]
            .astype(str).str.strip().str.lower()
            .map({'true': 1, 'false': 0})
            .fillna(0)
            .values
        )
    else:
        detected_col = np.zeros(len(sat_data))

    points = np.column_stack((
        sat_data["x"].values,
        sat_data["y"].values,
        sat_data["z"].values,
        sat_data["t"].values,
        sat_data["firing"].values if "firing" in sat_data.columns else np.zeros(len(sat_data)),
        detected_col
    ))

    if "nom_station" in sat_data.columns:
        station_names = sat_data["nom_station"].fillna("").astype(str).values
    else:
        station_names = np.array([""] * len(sat_data))

    if len(points) == 0:
        print(f"Warning: No data for satellite {sat_name}")
        continue

    satellite_mesh  = pv.Sphere(radius=100000, center=points[0][:3])
    satellite_actor = plotter.add_mesh(satellite_mesh, color=base_color, smooth_shading=True)

    trail_actors = []
    trail_meshes = []
    for j in range(trail_length):
        sphere = pv.Sphere(radius=50000, center=points[0][:3])
        actor  = plotter.add_mesh(sphere, color=base_color, smooth_shading=True)
        trail_actors.append(actor)
        trail_meshes.append(sphere)

    satellites.append({
        'name'          : sat_name,
        'points'        : points,
        'station_names' : station_names,
        'mesh'          : satellite_mesh,
        'actor'         : satellite_actor,
        'trail_meshes'  : trail_meshes,
        'trail_actors'  : trail_actors,
        'base_color'    : base_color,
        'is_noisy'      : is_noisy,
    })

    print(f"Added satellite {sat_name} with {len(points)} points")

# --- Time display ---
plotter.add_text("Elapsed: 00:00:00", position="upper_left",
                 font_size=12, color="white", name="time_display")

# ---------------------------------------------------------------------------
# Animation
# ---------------------------------------------------------------------------
print("Generating frames...")
frames = get_time_based_frames(satellites, frame_interval=60.0)
print(f"Generated {len(frames)} frames")

if len(frames) == 0:
    print("No frames generated. Check your data.")
    plotter.show()
else:
    plotter.show(interactive_update=True, full_screen=True)

    prev_t = None

    for frame_idx, (frame_start, frame_end, frame_indices) in enumerate(frames):

        # ── Current simulation time ────────────────────────────────
        current_t = frame_start
        for sat, indices in zip(satellites, frame_indices):
            if len(indices) > 0:
                current_t = sat['points'][indices[-1]][3]
                break

        # ── Real delta_t ───────────────────────────────────────────
        delta_t = 0.0 if prev_t is None else current_t - prev_t
        prev_t  = current_t

        # ── Time display ───────────────────────────────────────────
        hours   = int(current_t // 3600)
        minutes = int((current_t % 3600) // 60)
        seconds = int(current_t % 60)
        plotter.add_text(f"Elapsed: {hours:02d}:{minutes:02d}:{seconds:02d}",
                         position="upper_left", font_size=12, color="white",
                         name="time_display")

        # ── Earth rotation ─────────────────────────────────────────
        # ✅ sidereal day (86164.1 s), not solar day (86400 s)
        delta_angle           = (delta_t / 86164.1) * 360.0
        total_earth_rotation += delta_angle
        Earth.rotate_z(delta_angle, inplace=True)

        # ── Single rotation matrix: initial angle + all accumulated rotation
        # This is applied to raw ECEF points — zero drift possible
        R_mat = rotation_matrix_z(initial_earth_angle + total_earth_rotation)

        for gs in listGS:
            gs_name, gs_sphere, cone_mesh, orig_pts, orig_center, activation = gs

            # Recompute cone from raw ECEF original points
            rotated_pts         = (R_mat @ orig_pts.T).T
            cone_mesh.points[:] = rotated_pts

            # Recompute sphere center from raw ECEF original center
            rotated_center = R_mat @ orig_center
            translation    = rotated_center - np.array(gs_sphere.center)
            gs_sphere.translate(translation, inplace=True)

        # ── Station label ──────────────────────────────────────────
        label_lines = []
        for sat, indices in zip(satellites, frame_indices):
            if len(indices) == 0:
                continue
            idx      = indices[-1]
            detected = sat['points'][idx][5] == 1
            if detected:
                station = sat['station_names'][idx]
                if station and station not in ("", "NA", "nan", "None"):
                    label_lines.append(f"{sat['name']} -> {station}")

        plotter.add_text(
            "\n".join(label_lines) if label_lines else "",
            position="upper_right",
            font_size=11,
            color="yellow",
            name="station_label"
        )

        # ── Satellites ─────────────────────────────────────────────
        for sat, indices in zip(satellites, frame_indices):
            if len(indices) == 0:
                continue

            idx         = indices[-1]
            new_center  = sat['points'][idx][:3]
            translation = new_center - sat['mesh'].center
            sat['mesh'].translate(translation, inplace=True)

            is_noisy = sat['is_noisy']
            detected = sat['points'][idx][5] == 1

            if is_noisy:
                color = [0.0, 0.0, 1.0] if detected else [1.0, 1.0, 1.0]
            else:
                color = [0.0, 1.0, 0.0] if detected else [1.0, 0.0, 0.0]

            sat['actor'].prop.color = color

            for j in range(len(sat['trail_meshes']) - 1, 0, -1):
                sat['trail_meshes'][j].points[:] = sat['trail_meshes'][j - 1].points[:]
                fade = 1.0 - (j / trail_length) * 0.7
                sat['trail_actors'][j].prop.color = [c * fade for c in color]

            sat['trail_meshes'][0].points[:] = sat['mesh'].points[:]
            sat['trail_actors'][0].prop.color = color

        plotter.update()
        time.sleep(0.01)

        if (frame_idx + 1) % 10 == 0:
            print(f"Processed frame {frame_idx + 1}/{len(frames)}")

    plotter.show()

print("Animation complete!")