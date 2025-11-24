import pyvista as pv
from pyvista import examples
import pandas as pd
import numpy as np
import time
import os
import glob

def latlon_to_ecef(lat_deg, lon_deg, alt_km=0, R=6378100):
    lat = np.radians(lat_deg)
    lon = np.radians(lon_deg)
    r = R + alt_km
    x = r * np.cos(lat) * np.cos(lon)
    y = r * np.cos(lat) * np.sin(lon)
    z = r * np.sin(lat)
    return np.array([x, y, z])

def rotate_earth(Earth, delta_t, list_GS=[]):
    rotation_angle = (delta_t / 86400.0) * 360.0
    Earth.rotate_z(rotation_angle, inplace=True)
    for gs in list_GS:
        gs_name, gs_sphere, activation = gs
        gs_sphere.rotate_z(rotation_angle, inplace=True)

def get_time_based_frames(satellites, frame_interval=60.0):
    all_times = []
    for sat in satellites:
        all_times.extend(sat['points'][:, 3])
    
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

# --- Load all CSV files ---
pv.close_all()
script_dir = os.path.dirname(os.path.abspath(__file__))
filenames = glob.glob(os.path.join(script_dir, "*.csv"))
filenames = [os.path.basename(f) for f in filenames]

# --- Create plotter and Earth ---
plotter = pv.Plotter()
texture = examples.load_globe_texture()
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000, 1000, 1000], inplace=True)
Earth.rotate_z(180, inplace=True)

plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# --- CSV load ---
GS_csv = pd.read_csv("src/main/java/com/example/Ground_stations/GS_coordinates.csv")
listGS = []

for idx, row in GS_csv.iterrows():
    name = row["name"]
    lat = float(row["lat"])
    lon = float(row["long"])
    alt = float(row["alt"])
    activation = bool(row["activated"])

    pos = latlon_to_ecef(lat, lon, alt)
    gs_sphere = pv.Sphere(radius=1.5e5, center=pos)
    color = "green" if activation else "red"
    plotter.add_mesh(gs_sphere, color=color, smooth_shading=True, name=f"gs_{name}")
    listGS.append((name, gs_sphere, activation))

# --- Satellite setup ---
trail_length = 15 
colors = [
    [1.0, 0.0, 0.0],
    [0.0, 1.0, 0.0],
    [0.0, 0.0, 1.0],
    [1.0, 1.0, 0.0],
    [1.0, 0.0, 1.0],
    [0.0, 1.0, 1.0],
]

satellites = []

for filename in filenames:
    df = pd.read_csv("src/main/java/com/example/View/" + filename)
    points = np.column_stack((df["x"], df["y"], df["z"], df["t"], df["firing"], df["detected_by_GS"]))
    print(df["x"].values)
    print(df["y"].values)
    print(df["z"].values)
    is_noisy = filename.find("noisy") != -1
    base_color = [1.0, 0.0, 0.0] if not is_noisy else [0.0, 0.0, 1.0]  # Red for true, Blue for noisy
    
    # Satellite mesh et actor
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
        'points': points,
        'mesh': satellite_mesh,
        'actor': satellite_actor,
        'trail_meshes': trail_meshes,
        'trail_actors': trail_actors,
        'base_color': base_color,
        'is_noisy': is_noisy 
    })

# --- Animation ---
frames = get_time_based_frames(satellites, frame_interval=60.0)

plotter.show(interactive_update=True, full_screen=True)

for frame_start, frame_end, frame_indices in frames:
    #print(f"Frame: {frame_start} to {frame_end} seconds")
    #Every frame we rotate earth and the position of the GS
    rotate_earth(Earth, frame_end - frame_start, listGS)
    
    for sat, indices in zip(satellites, frame_indices):
        if len(indices) == 0:
            continue
        
        # Prendre le dernier point du frame (plus efficace)
        idx = indices[-1]
        
        # Update satellite position
        new_center = sat['points'][idx][:3]
        translation = new_center - sat['mesh'].center
        sat['mesh'].translate(translation, inplace=True)
        is_noisy = sat['is_noisy']
        detected = sat['points'][idx][5] == 1

        if is_noisy:
            # Satellite noisy: bleu si detected, blanc sinon
            color = [0.0, 0.0, 1.0] if detected else [1.0, 1.0, 1.0]
        else:
            # Satellite vrai: vert si detected, rouge sinon
            color = [0.0, 1.0, 0.0] if detected else [1.0, 0.0, 0.0]
        
        # Update actor color 
        sat['actor'].prop.color = color
        
        # Update trail
        for j in range(len(sat['trail_meshes']) - 1, 0, -1):
            sat['trail_meshes'][j].points[:] = sat['trail_meshes'][j - 1].points[:]
            fade = 1.0 - (j / trail_length) * 0.7
            sat['trail_actors'][j].prop.color = [c * fade for c in color]
        
        # Premier élément du trail
        sat['trail_meshes'][0].points[:] = sat['mesh'].points[:]
        sat['trail_actors'][0].prop.color = color

    
    plotter.update()
    time.sleep(0.01)

plotter.show()