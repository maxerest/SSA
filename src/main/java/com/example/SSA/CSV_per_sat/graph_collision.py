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

    # Get all unique satellites from the first column (nom_sat)
    unique_satellites = df['nom_sat'].unique()

    # Create one plot per unique satellite
    for main_satellite in unique_satellites:
        if plot_count >= max_plots:
            break

        # Filter data for this main satellite
        sat_df = df[df['nom_sat'] == main_satellite]

        # Check if there are any non-zero collision values
        if (sat_df['% collision'] != 0.00).any():
            # Create a new figure for this satellite
            plt.figure(figsize=(12, 7))

            # Get all unique satellites that this one has collisions with
            collision_satellites = sat_df['nom_autre_sat'].unique()

            # Plot all satellites with non-zero collision values
            for collision_sat in collision_satellites:
                collision_data = sat_df[sat_df['nom_autre_sat'] == collision_sat]

                # Check if there are any non-zero collision values for this pair
                if (collision_data['% collision'] != 0.00).any():
                    plt.plot(collision_data['t'], collision_data['% collision'],
                             marker='o', linestyle='-', linewidth=2, markersize=6,
                             label=collision_sat)

            plt.xlabel('Time (t)')
            plt.ylabel('Collision Percentage (%)')
            plt.title(f'Collision Risk - {main_satellite}')
            plt.legend(loc='best', fontsize=9)
            plt.grid(True, alpha=0.3)
            plt.tight_layout()

            plot_count += 1
            print(f"Created plot {plot_count}: {main_satellite}")
        else:
            print(f"No collision risk found for {main_satellite} - skipping")

print(f"Total plots created: {plot_count}")
plt.show()