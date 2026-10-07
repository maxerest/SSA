A Java-based Propagation project using the Orekit library

Key Features

Satellite propagation
Manoeuvres of satellites
SATCOM capabilities
EO observations
Intersatellite links
3D and 2D view of all the information
Basic SSA (not mature enough yet)
Number of satellites in view

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
Depends on what you want to use

Usage
Step 1: Configure all relevant CSV files (there are .example. where you need to change the name without the .example. and file it)
Careful, about the formating and filling all the csv files

Step 2: Launch the App.java as it is the core of the file
     2.1, click on configure to create the full constellation: make sure to have an epoch date, a duration date, chose any subsytem needed depending on your need (ex : an EO sensor to do EO observation)
     2.2, once the configurator done, the constellation will appear around the globe. Click on Propagate to launch the propagation. on the top left you will see the progress.
     2.3, you will have the satellite in motion on the view, you can click on a single sat to have the info, you can clik on fround track to have the 2D view

Step 3 optional :
    Once the propagation is done, all the relevant CSV file are created, allowing for the launch of the other relevant Python programs
    For the moment you need to go on the project folder to launch the specific python file
    here are the following python:
    "nb_sat_visible_at_GS" for at any GS defined


Technologies Used
Java: Core application development
Orekit: Orbital mechanics and propagation library
Kalman Filter: Orbit estimation algorithm
Python: Data visualization and analysis
Maven: Project build management
