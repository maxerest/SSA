import pandas as pd
import matplotlib.pyplot as plt
import os

folder_path = 'src/main/java/com/example/SSA/CSV_per_sat'  # Change to your folder path

# Get all CSV files in the folder
csv_files = [f for f in os.listdir(folder_path) if f.endswith('.csv')]

plot_count = 0
max_plots = 20

for csv_file in csv_files:
    if plot_count >= max_plots:
        print(f"Reached maximum of {max_plots} plots. Stopping...")
        break
    file_path = os.path.join(folder_path, csv_file)
    df = pd.read_csv(file_path)

    # Filter out satellites where ALL values in '% collision' are 0.00
    satellites_to_plot = []
    for satellite in df['nom_autre_sat'].unique():
        sat_data = df[df['nom_autre_sat'] == satellite]
        if (sat_data['% collision'] != 0.00).any():# Keep if any non-zero value exists
            satellites_to_plot.append(satellite)

    # Only create a plot if there are satellites with non-zero collision values
    if satellites_to_plot:
        # Create a new figure for each CSV file with non-zero collisions
        plt.figure(figsize=(10, 6))

        for satellite in satellites_to_plot:
            sat_data = df[df['nom_autre_sat'] == satellite]
            if (sat_data['% collision'] != 0.00).any():
                plt.plot(sat_data['t'], sat_data['% collision'], marker='o', label=satellite)

        plt.xlabel('Time (t)')
        plt.ylabel('Collision Percentage (%)')
        plt.title(f'Collision Percentage - {csv_file}')
        plt.legend()
        plt.grid(True)
        plt.tight_layout()

        plot_count += 1
    else:
        print(f"No collision data found in {csv_file} - skipping plot")

plt.show()