SSA - Space Situational Awareness
A Java-based Space Situational Awareness project doing satellite orbit estimation and propagation using the Orekit library and Kalman filtering techniques. This project propagates theoretical satellite orbits and estimates them using simulated real observation data, with visualization capabilities in Python.

Overview
This project was developed as a learning exercise in Java and Python, focusing on practical applications of orbital mechanics and filtering algorithms. It uses the Orekit library to handle orbital calculations and implements a Kalman filter to estimate satellite positions from ground-based observations.
Key Features

Theoretical satellite orbit creation and propagation using Orekit
Kalman filter implementation for orbit estimation
Least Square methodes to estimates the satellite position
Manoeuvre of satellites
Multi-satellite support with independent tracking
Ground station coordinate management
Visualization of real vs. estimated orbits using Python

Project Structure
SSA/
├── src/                          # Java source code
│   ├── com.example.App.java                 # Main application entry point
│   └── [orbital mechanics & filtering classes]
├── orekit-data/                 # Orekit library data files
├── pom.xml                      # Maven configuration
├── GS_coordinates.CSV           # Ground station definitions (coordinates and if active)
├── README.txt                   # Additional documentation
└── [Python visualization scripts]
Prerequisites

Java 8 or higher
Maven
Python 3.x (for visualization)
Orekit library (handled via Maven dependency)

Installation

Clone the repository:

bashgit clone https://github.com/maxerest/SSA.git
cd SSA

Build the project using Maven:

bashmvn clean install

Install Python visualization dependencies:

bashpip install matplotlib numpy scipy
Usage
Step 1: Configure Ground Stations
Edit the GS_coordinates.CSV file to specify the ground stations you want to use. Each entry should include:

Station name
Geographic coordinates (latitude, longitude, altitude)
Status (active/inactive)

Step 2: Set Up Satellites
In com.example.App.java, configure:
If you want only the simulated real satellite, if you want Kalman and LSB orbit determination
Number of "real" satellites to create and estimate

Orbital parameters (semi-major axis, eccentricity, inclination, etc.) in the method "real_orbit"

In Parametres.java, configure: 
Simulation duration and time step

Step 3: Run the Application
When prompted, provide:
If the program should implement your values or random orbit values (recommended to use your as random value can create impossible orbits)

Step 4: Visualize Results
Through the python plot or analysis through the CSB Files

How It Works
1. Orbit Setup
The application creates a perfect theoretical satellite orbit based on classical orbital elements and an numerical propagator. This represents the "true" satellite state.
2. Propagation
Using Orekit's propagation engine, the satellite orbit is propagated forward in time under realistic orbital mechanics, accounting for perturbations and other forces.
3. Observation Simulation
Ground stations track the satellite and generate observations at regular intervals. These observations contain  measurement noise.
4. Orbit Estimation
4.1A Kalman filter processes the noisy observations to estimate the satellite's orbit, progressively improving its estimate as more data becomes available.
4.2 Estimated measurement from the ground satations are created and feeded into the LSB method to create an estimate position
5. Visualization
Python scripts generate plots comparing:

True satellite positions vs. estimated positions
Estimation error over time
Kalman filter convergence

Important Notes
⚠️ Valid Orbital Parameters Required: The application requires physically valid orbital parameters. If input orbital parameters are incorrect (e.g., violating orbital mechanics laws), the estimation will fail. Common validation issues include:

Configuration Files
GD_coordinates.CSV
Format: name,latitude,longitude,altitude,active
Example:
Kourou,5.24,-52.80,91.0,true
Cape Canaveral,28.47,-80.54,0.0,true
Baikonur,45.96,63.56,106.0,false

Technologies Used
Java: Core application development
Orekit: Orbital mechanics and propagation library
Kalman Filter: Orbit estimation algorithm
Python: Data visualization and analysis
Maven: Project build management

Project Goals
This project was developed to:

Deepen understanding of Java programming
Learn practical orbital mechanics
Implement and understand Kalman filtering algorithms
Least batch square method
Gain experience with real-world aerospace software patterns
Develop Python visualization skills

Troubleshooting
Issue: "Invalid orbital parameters"

Issue: Visualization script fails

Verify Java output files are generated in the correct directory

Issue: Kalman filter not converging

Check ground station coverage and observation frequency
Verify measurement noise parameters are realistic
Ensure sufficient computational time steps
