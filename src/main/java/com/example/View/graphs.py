import pandas as pd
import matplotlib.pyplot as plt
import glob
import os
from tkinter import Tk, filedialog

def get_script_folder():
    """Get the folder where the Python script is located"""
    return os.path.dirname(os.path.abspath(__file__))

def get_orbital_param_files(folder_path):
    """Get all CSV files containing 'Orbital_param' in the folder"""
    csv_files = glob.glob(os.path.join(folder_path, "*Orbital_param*.csv"))
    return sorted(csv_files)

def plot_csv_data(file_path,sample_rate=3):
    """Read CSV and create plots - first 3 columns are values, 4th is time"""
    try:
        df = pd.read_csv(file_path)
        df = df.iloc[::sample_rate]

        # Get first 3 columns as values and 4th as time
        value_cols = df.columns[1:4].tolist()
        time_col = df.columns[4] if len(df.columns) > 3 else df.index

        # Create a figure with 3 subplots (one for each value column)
        fig, axes = plt.subplots(3, 1, figsize=(12, 10))
        file_name = os.path.basename(file_path)
        fig.suptitle(f"Data from {file_name}", fontsize=14, fontweight='bold')

        # Plot each of the first 3 columns vs time
        for i, column in enumerate(value_cols):
            axes[i].plot(df[time_col], df[column], marker='o', linestyle='-', linewidth=2, color='steelblue')
            axes[i].set_title(f"{column} vs {time_col}", fontsize=12, fontweight='bold')
            axes[i].set_xlabel(time_col)
            axes[i].set_ylabel(column)
            axes[i].grid(True, alpha=0.3)

        plt.tight_layout()

    except Exception as e:
        print(f"Error reading file {file_path}: {e}")



def main():

    folder_path = get_script_folder()

    # Get all Orbital_param files
    csv_files = get_orbital_param_files(folder_path)

    if not csv_files:
        print("No CSV files with 'Orbital_param' found in this folder!")
        return
    plt.close('all')
    # Plot each file
    for file_path in csv_files:
        plot_csv_data(file_path)
    plt.show()


if __name__ == "__main__":
    main()