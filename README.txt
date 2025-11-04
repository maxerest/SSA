This project has been developped by me to develop my Java and Python skills.
The goal of this project is to use the Orekit library in Java to estimate an orbit from a theoretical perfect satellite propoagation. To do do the project follows the following logic:
1- Sets up a perfect satellite orbit from the "real_orbit"
2- Propagats that satellite for the duration of the simulation
3- Creates an estimate satellite orbit from this real orbit.
4- Draws both real and estimated satellite in Python to show the evolution of the Kalman filter.

If you want to use this project you need to do the following:
-Check in the "GD_coordinates.CSV" file, you have the ground station you want (defined by name, position and if it is currently on or not)
-In the App.java file, enter the number of "real" sats you want to create that will then be estimated
-Launch the file and enjoy the view

For each step here is an 