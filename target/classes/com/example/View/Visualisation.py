import pyvista as pv
from pyvista import examples
from PIL import Image
import pandas as pd
import numpy as np
import time

"""
Create a textured sphere (Earth) using PyVista
"""
# Creation of the plot
plotter = pv.Plotter()

# Get Eath inforamtion, set size and rotate for good orientation
Earth = examples.planets.load_earth(radius=6378.1)
Earth.scale([1000,1000,1000], inplace=True)
Earth.rotate_z(180, inplace=True)
texture = examples.load_globe_texture()
plotter.add_background_image(examples.planets.download_stars_sky_background(load=False))
plotter.add_mesh(Earth, texture=texture, smooth_shading=True)


# Load orbit points
df = pd.read_csv("orbit.csv")
points = np.column_stack((df['x'], df['y'], df['z']))



# Satellite mesh
satellite_mesh = pv.Sphere(radius=100000, center=points[0])
satellite_actor = plotter.add_mesh(satellite_mesh, color="red", smooth_shading=True)

plotter.show(interactive_update=True,full_screen=True)
# Trail setup: pre-create spheres
trail_length = 40
trail_spheres = []
for j in range(trail_length):
    trail_spheres.append([0,0,[0,0,0]])
    sphere = pv.Sphere(radius=100000, center=points[j])
    sphere['colors']=np.zeros((sphere.n_points, 4)) 
    actor = plotter.add_mesh(sphere, scalars='colors', rgb=True, smooth_shading=True)
    trail_spheres[j][0]=actor
    trail_spheres[j][1]=sphere

for i in range(len(points)):

    satellite_mesh.points[:] = pv.Sphere(radius=100000, center=points[i]).points
    newest = trail_spheres[-1]
    newest[1].points[:] = satellite_mesh.points[:]
    newest[1]["colors"] = np.tile([1.0,0.0,0.0,1], (newest[1].n_points,1))  # bright red

    #Update trail spheres
    start = min(i,  trail_length )
    for j in range(start-1):
        fade_factor = 0.8*j/(start-1)
        mesh = trail_spheres[j][1]
        mesh.points[:] = trail_spheres[j+1][1].points[:]
        mesh["colors"] = np.tile([fade_factor,0.0,0.0,1.0], (mesh.n_points,1))
        
        

    plotter.update()
    time.sleep(0.05)
plotter.show(full_screen=True)
