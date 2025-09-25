import pyvista as pv
from pyvista import examples
import pandas as pd
import numpy as np
import time
import sys

# --- Load CSV filenames from command line ---
filenames = [arg + ".csv" for arg in sys.argv[1:]]
if not filenames:
    print("Usage: python Visualisation.py sat1 sat2 ...")
    sys.exit(1)
# --- Create plotter and Earth ---
plotter = pv.Plotter()
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000, 1000, 1000], inplace=True)
Earth.rotate_z(180, inplace=True)
texture = examples.load_globe_texture()
plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# --- Ground stations ---
def latlon_to_ecef(lat_deg, lon_deg, alt_km=0, R=6378100):
    lat = np.radians(lat_deg)
    lon = np.radians(lon_deg)
    r = R + alt_km
    x = r * np.cos(lat) * np.cos(lon)
    y = r * np.cos(lat) * np.sin(lon)
    z = r * np.sin(lat)
    return np.array([x, y, z])

# --- CSV load ---
GS_csv = pd.read_csv("src/main/java/com/example/Ground_stations/GS_coordinates.csv")

# --- Plot each station ---
for idx, row in GS_csv.iterrows():
    name = row["name"]
    lat = float(row["lat"])       # CSV: lat column
    lon = float(row["long"])      # CSV: long column
    alt = float(row["alt"])       # altitude in km
    activation = bool(row["activated"])  # 1 if active, 0 if not

    # Convert to ECEF (and scale/lift)
    pos = latlon_to_ecef(lat, lon, alt )    # +50 m for visibility
    pos = np.array([pos[0], pos[1], pos[2]])

    # Add station sphere
    gs_sphere = pv.Sphere(radius=1.5e5, center=pos)
    color = "white" if activation  else "red"
    plotter.add_mesh(
        gs_sphere,
        color=color,
        smooth_shading=True,
        name=f"gs_{name}"   # unique ID per ground station
    )

# --- Satellite setup ---
trail_length = 20
colors = [
    [1.0, 0.0, 0.0],  # red
    [0.0, 1.0, 0.0],  # green
    [0.0, 0.0, 1.0],  # blue
    [1.0, 1.0, 0.0],  # yellow
    [1.0, 0.0, 1.0],  # magenta
    [0.0, 1.0, 1.0],  # cyan
]

satellites = []

for idx, filename in enumerate(filenames):
    df = pd.read_csv("src/main/java/com/example/View/" + filename)
    # points: x, y, z, t, firing
    points = np.column_stack((df["x"], df["y"], df["z"], df["t"], df["firing"]))
    base_color = colors[idx % len(colors)]

    # Satellite mesh and actor
    satellite_mesh = pv.Sphere(radius=100000, center=points[0][:3])
    satellite_actor = plotter.add_mesh(satellite_mesh, color=base_color, smooth_shading=True)

    # Trail meshes
    trail_spheres = []
    for j in range(trail_length):
        sphere = pv.Sphere(radius=50000, center=points[0][:3])
        actor = plotter.add_mesh(sphere, color=base_color, smooth_shading=True)
        trail_spheres.append((actor, sphere))

    satellites.append([points, satellite_mesh, satellite_actor, trail_spheres, base_color])

# --- Animation ---
plotter.show(interactive_update=True, full_screen=True)

n_frames = max(len(sat[0]) for sat in satellites)
for i in range(n_frames):
    for points, sat_mesh, sat_actor, trail_spheres, base_color in satellites:
        if i < len(points):
            # Update satellite position
            sat_mesh.translate(points[i][:3] - sat_mesh.center, inplace=True)

            # Update color based on firing flag
            if points[i][4] == 1:
                color=[1.0, 0.5, 0.0] # orange when firing
            else:
                color=base_color
            sat_actor =plotter.add_mesh(sat_mesh, color= color, smooth_shading=True)   
            # Update trail: shift older points down the trail
            for j in range(trail_length - 1, 0, -1):
                trail_spheres[j][1].points[:] = trail_spheres[j - 1][1].points[:]
            # Add newest satellite position at start of trail
            trail_spheres[0][1].points[:] = sat_mesh.points[:]
            # Optionally fade trail colors
            for j, (actor, sphere) in enumerate(trail_spheres):
                fade = 1.0 - (j / trail_length) * 0.7
                actor.prop.color = [c * fade for c in base_color]

    plotter.update()
    time.sleep(0.02)

plotter.show()
