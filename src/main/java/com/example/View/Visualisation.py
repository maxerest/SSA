import pandas as pd
import plotly.graph_objects as go

# Read Orekit data
df = pd.read_csv("orbit.csv")

# Create 3D plot
fig = go.Figure()

# Earth as a sphere
import numpy as np
u, v = np.mgrid[0:2*np.pi:50j, 0:np.pi:25j]
x = 6371000 * np.cos(u) * np.sin(v)  # Earth radius in meters
y = 6371000 * np.sin(u) * np.sin(v)
z = 6371000 * np.cos(v)
fig.add_trace(go.Surface(x=x, y=y, z=z, colorscale='Blues', opacity=0.7))

# Satellite orbit
fig.add_trace(go.Scatter3d(
    x=df['x'], y=df['y'], z=df['z'],
    mode='lines+markers',
    line=dict(color='red', width=3),
    name='Satellite Orbit'
))

fig.update_layout(scene=dict(
    xaxis_title='X (m)', yaxis_title='Y (m)', zaxis_title='Z (m)',
    aspectmode='data'
))

fig.show()