import pyvista as pv
from pyvista import examples
from PIL import Image
import pandas as pd
import numpy as np

"""
Create a textured sphere (Earth) using PyVista
"""
# Load image using PIL
plotter = pv.Plotter(window_size=[600,600])
plotter.set_background('black')
# Flip vertically
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000,1000,1000], inplace=True)
Earth.rotate_z(-180, inplace=True)
mesh = examples.planets.load_earth()
texture = examples.load_globe_texture()
image_path = examples.planets.download_stars_sky_background(load=False)

plotter.add_background_image(image_path)
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)

# Convert back to array and feed to PyVista texture
df = pd.read_csv("orbit.csv")
points = np.column_stack((df['x'], df['y'], df['z']))
plotter.add_points(points, color="red", point_size=5, render_points_as_spheres=True)



plotter.show(title="Orbit sats")
