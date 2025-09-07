import pyvista as pv
from pyvista import examples
import pandas as pd
import numpy as np
import time
import sys

# --- Récupération des fichiers depuis la ligne de commande ---
filenames = [arg + ".csv" for arg in sys.argv[1:]]
if not filenames:
    print("Usage: python Visualisation.py sat1 sat2 ...")
    sys.exit(1)

# --- Création de la scène ---
plotter = pv.Plotter()
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000, 1000, 1000], inplace=True)
Earth.rotate_z(180, inplace=True)
texture = examples.load_globe_texture()
plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# --- Chargement des satellites ---
satellites = []  # liste qui contiendra [points, mesh, actor, trail_spheres]
trail_length = 20

colors = [
    [1.0, 0.0, 0.0],  # rouge
    [0.0, 1.0, 0.0],  # vert
    [0.0, 0.0, 1.0],  # bleu
    [1.0, 1.0, 0.0],  # jaune
    [1.0, 0.0, 1.0],  # magenta
    [0.0, 1.0, 1.0],  # cyan
]

for idx, filename in enumerate(filenames):
    print(filename)
    df = pd.read_csv("src/main/java/com/example/View/" + filename)
    points = np.column_stack((df["x"], df["y"], df["z"]))

    # Couleur unique par satellite
    base_color = colors[idx % len(colors)]

    # Satellite mesh
    satellite_mesh = pv.Sphere(radius=100000, center=points[0])
    satellite_actor = plotter.add_mesh(satellite_mesh, color=base_color, smooth_shading=True)

    # Trail setup
    trail_spheres = []
    for j in range(trail_length):
        trail_spheres.append([0, 0, [0, 0, 0]])
        sphere = pv.Sphere(radius=100000, center=points[j % len(points)])
        sphere["colors"] = np.zeros((sphere.n_points, 4))
        actor = plotter.add_mesh(sphere, scalars="colors", rgb=True, smooth_shading=True)
        trail_spheres[j][0] = actor
        trail_spheres[j][1] = sphere

    satellites.append([points, satellite_mesh, satellite_actor, trail_spheres, base_color])

# --- Animation ---
plotter.show(interactive_update=True, full_screen=True)

n_frames = max(len(sat[0]) for sat in satellites)
for i in range(n_frames):
    for points, sat_mesh, _, trail_spheres, base_color in satellites:
        if i < len(points):
            # Update satellite position
            sat_mesh.points[:] = pv.Sphere(radius=100000, center=points[i]).points

            # Update trail
            newest = trail_spheres[-1]
            newest[1].points[:] = sat_mesh.points[:]
            newest[1]["colors"] = np.tile(base_color + [1.0], (newest[1].n_points, 1))

            start = min(i, trail_length)
            for j in range(start - 1):
                fade_factor = 0.8 * j / (start - 1)
                mesh = trail_spheres[j][1]
                mesh.points[:] = trail_spheres[j + 1][1].points[:]
                mesh["colors"] = np.tile(
                    [base_color[0] * fade_factor,
                     base_color[1] * fade_factor,
                     base_color[2] * fade_factor,
                     1.0],
                    (mesh.n_points, 1)
                )

    plotter.update()
    time.sleep(0.02)

plotter.show()
