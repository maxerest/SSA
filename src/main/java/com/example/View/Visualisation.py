import pyvista as pv
from pyvista import examples
import pandas as pd
import numpy as np
import time
import os
from tkinter import Tk, filedialog

def latlon_to_ecef(lat_deg, lon_deg, alt_km=0, R=6378100):
    """Convert latitude/longitude to ECEF coordinates"""
    lat = np.radians(lat_deg)
    lon = np.radians(lon_deg)
    r = R + alt_km
    x = r * np.cos(lat) * np.cos(lon)
    y = r * np.cos(lat) * np.sin(lon)
    z = r * np.sin(lat)
    return np.array([x, y, z])

def rotate_earth(Earth, delta_t, list_GS=[]):
    """Rotate Earth and ground stations"""
    rotation_angle = (delta_t / 86400.0) * 360.0
    Earth.rotate_z(rotation_angle, inplace=True)
    for gs in list_GS:
        gs_name, gs_sphere, activation = gs
        gs_sphere.rotate_z(rotation_angle, inplace=True)

def get_time_based_frames(satellites, frame_interval=60.0):
    """Generate frames based on time intervals"""
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
            mask = (sat['points'][:, 3] >= frame_start) & (sat['points'][:, 3] < frame_end)
            indices = np.where(mask)[0]
            frame_indices.append(indices)
        frames.append((frame_start, frame_end, frame_indices))

    return frames

def select_csv_file():
    file_path = "src/main/java/com/example/View/real_sats.csv"
    if os.path.exists(file_path):
        return file_path
    else:
        return "src/main/java/com/example/View/TLE.csv"

def select_gs_csv_file():
    return "src/main/java/com/example/Ground_stations/GS_coordinates.csv"

# --- Initialize ---
pv.close_all()

# --- Load satellite CSV ---
sat_file = select_csv_file()
if not sat_file:
    print("No satellite file selected!")
    exit()

print(f"Loading satellite data from: {sat_file}")

# --- Create plotter and Earth ---
plotter = pv.Plotter()
texture = examples.load_globe_texture()
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000, 1000, 1000], inplace=True)
Earth.rotate_z(180, inplace=True)

plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# --- Load Ground Stations (optional) ---
listGS = []
try:
    gs_file = select_gs_csv_file()
    if gs_file and os.path.exists(gs_file):
        GS_csv = pd.read_csv(gs_file)

        for idx, row in GS_csv.iterrows():
            name = row["name"] if "name" in row else f"GS_{idx}"
            lat = float(row["lat"])
            lon = float(row["long"]) if "long" in row else float(row["lon"])
            alt = float(row["alt"]) if "alt" in row else 0
            activation = bool(row["activated"]) if "activated" in row else True

            pos = latlon_to_ecef(lat, lon, alt)
            gs_sphere = pv.Sphere(radius=1.5e5, center=pos)
            color = "green" if activation else "red"
            plotter.add_mesh(gs_sphere, color=color, smooth_shading=True, name=f"gs_{name}")
            listGS.append((name, gs_sphere, activation))
            print(f"Added ground station: {name}")
except Exception as e:
    print(f"Could not load ground stations: {e}")

# --- Load satellite data from single CSV ---
df = pd.read_csv(sat_file)

# Get unique satellite names
sat_names = df['name_sat'].unique()
print(f"Found {len(sat_names)} satellites: {sat_names}")

# Define colors for satellites
base_colors = {
    'Sat_real_1': [1.0, 0.0, 0.0],      # Red
    'Sat_real_2': [0.0, 0.0, 1.0],      # Blue
    'Sat_real_3': [0.0, 1.0, 0.0],      # Green
    'Sat_real_4': [1.0, 1.0, 0.0],      # Yellow
    'Sat_real_5': [1.0, 0.0, 1.0],      # Magenta
    'Sat_real_6': [0.0, 1.0, 1.0],      # Cyan
}

# --- Satellite setup ---
trail_length = 15
satellites = []

for sat_name in sat_names:
    # Filter data for this satellite
    sat_data = df[df['name_sat'] == sat_name]

    # Check if satellite is "noisy" (can be based on naming or other criteria)
    is_noisy = 'noisy' in sat_name.lower()

    # Get base color
    base_color = base_colors.get(sat_name, [0.5, 0.5, 0.5])

    # Convert to numpy array with required columns: x, y, z, t, firing, detected_by_GS
    points = np.column_stack((
        sat_data["x"].values,
        sat_data["y"].values,
        sat_data["z"].values,
        sat_data["t"].values,
        sat_data["firing"].values if "firing" in sat_data.columns else np.zeros(len(sat_data)),
        sat_data["detected_by_GS"].values if "detected_by_GS" in sat_data.columns else np.zeros(len(sat_data))
    ))

    if len(points) == 0:
        print(f"Warning: No data for satellite {sat_name}")
        continue

    # Satellite mesh and actor
    satellite_mesh = pv.Sphere(radius=100000, center=points[0][:3])
    satellite_actor = plotter.add_mesh(satellite_mesh, color=base_color, smooth_shading=True)

    # Trail meshes
    trail_actors = []
    trail_meshes = []
    for j in range(trail_length):
        sphere = pv.Sphere(radius=50000, center=points[0][:3])
        actor = plotter.add_mesh(sphere, color=base_color, smooth_shading=True)
        trail_actors.append(actor)
        trail_meshes.append(sphere)

    satellites.append({
        'name': sat_name,
        'points': points,
        'mesh': satellite_mesh,
        'actor': satellite_actor,
        'trail_meshes': trail_meshes,
        'trail_actors': trail_actors,
        'base_color': base_color,
        'is_noisy': is_noisy
    })

    print(f"Added satellite {sat_name} with {len(points)} points")

# --- Animation ---
print("Generating frames...")
frames = get_time_based_frames(satellites, frame_interval=60.0)
print(f"Generated {len(frames)} frames")

if len(frames) == 0:
    print("No frames generated. Check your data.")
    plotter.show()
else:
    plotter.show(interactive_update=True, full_screen=True)

    for frame_idx, (frame_start, frame_end, frame_indices) in enumerate(frames):

        # Rotate earth and ground stations
        rotate_earth(Earth, frame_end - frame_start, listGS)

        for sat, indices in zip(satellites, frame_indices):
            if len(indices) == 0:
                continue

            # Take the last point in the frame
            idx = indices[-1]

            # Update satellite position
            new_center = sat['points'][idx][:3]
            translation = new_center - sat['mesh'].center
            sat['mesh'].translate(translation, inplace=True)

            is_noisy = sat['is_noisy']
            detected = sat['points'][idx][5] == 1

            if is_noisy:
                # Noisy satellite: blue if detected, white if not
                color = [0.0, 0.0, 1.0] if detected else [1.0, 1.0, 1.0]
            else:
                # Real satellite: green if detected, red if not
                color = [0.0, 1.0, 0.0] if detected else [1.0, 0.0, 0.0]

            # Update actor color
            sat['actor'].prop.color = color

            # Update trail
            for j in range(len(sat['trail_meshes']) - 1, 0, -1):
                sat['trail_meshes'][j].points[:] = sat['trail_meshes'][j - 1].points[:]
                fade = 1.0 - (j / trail_length) * 0.7
                sat['trail_actors'][j].prop.color = [c * fade for c in color]

            # First trail element
            sat['trail_meshes'][0].points[:] = sat['mesh'].points[:]
            sat['trail_actors'][0].prop.color = color

        plotter.update()
        time.sleep(0.01)

        # Print progress
        if (frame_idx + 1) % 10 == 0:
            print(f"Processed frame {frame_idx + 1}/{len(frames)}")

    plotter.show()

print("Animation complete!")