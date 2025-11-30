package com.example.TLE;
import com.example.View.Visulations;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class My_TLE {

    private static final String TLE_DATA_DIR = "src\\main\\java\\com\\example\\TLE";
    private static final String CELESTRAK_BASE = "https://celestrak.org/NORAD/elements/gp.php";
    public static String format = "TLE";
    // Types de TLE disponibles sur Celestrak
    public enum TLEType {
        
        ACTIVE("GROUP=active", "active.", "Satellites actifs"),
        LAST_30_DAYS("GROUP=last-30-days", "last-30-days.", "Satellites lancés dans les 30 derniers jours"),
        WEATHER("GROUP=weather", "weather.", "Satellites météorologiques"),
        STATIONS("GROUP=stations", "stations.", "Stations spatiales"),
        HYPERBOLIC("GROUP=hyperbolic", "hyperbolic.", "Satellites hyperboliques"),
        GEO_INACTIVE("GROUP=geo-inactive", "geo-inactive.", "Géostationnaires inactifs"),
        SPACEX("GROUP=starlink", "starlink.", "Constellation Starlink"),
        DECAYING("SPECIAL=DECAYING", "decaying.", "Satellites en décroissance");

        private final String query;
        private final String filename;
        private final String description;
        
        TLEType(String query, String filename, String description) {
            this.query = query;
            this.filename = filename;
            this.description = description;
        }

        public String getQuery() {
            return query;
        }

        public String getFilename() {
            return filename;
        }

        public String getDescription() {
            return description;
        }

        public String getUrl() {
            return CELESTRAK_BASE + "?" + query + "&FORMAT="+format;
        }
    }

    /**
     * Initialise et télécharge les TLE du type spécifié
     */
    public static void check_if_download_tle(TLEType tleType) throws Exception {
        System.out.println("Initialisation des TLE : " + tleType.getDescription());
        // Créer un scanner pour lire l'entrée
        Scanner scanner = new Scanner(System.in);
        System.out.print("Telecharger les fichiers? (true/false)");
        boolean download = scanner.nextBoolean();
        if (download)
            downloadTLEFiles(tleType);
        // loadAndDisplayTLEs(tleType);*

        scanner.close();
    }

    public static void main() {
        // Afficher les options disponibles
        My_TLE.displayAvailableTLETypes();
        List<SpacecraftState> states = null;
        // Choix du type de TLE
        Scanner scanner = new Scanner(System.in);

        System.out.print("Choisissez le numéro du type de TLE: \n");
        int choice = -1;

        // Vérifier que le choix est valide
        My_TLE.TLEType[] types = My_TLE.TLEType.values();
        do {
            System.out.print("Choisissez le numéro du type de TLE:(1-" + types.length + "): ");
            // Vérifier que c'est bien un nombre
            if (!scanner.hasNextInt()) {
                System.out.println(" Veuillez entrer un nombre!");
                scanner.nextLine(); // Consommer la mauvaise entrée
                continue;
            }
            choice = scanner.nextInt();
        } while (choice <= 0 || choice > types.length);
        My_TLE.TLEType selectedType = types[choice - 1];

        try {
            check_if_download_tle(selectedType);
        } catch (Exception e) {
            System.err.println("Erreur dans la méthode de télechargement des TLE : " + e.getMessage());
            e.printStackTrace();
        }
        try {
            states = create_OREKIT_Statecraft_state(selectedType.getFilename());
        } catch (Exception e) {
            System.err.println("Erreur dans la création des TLE orekit : " + e.getMessage());
            e.printStackTrace();
        }
        try {
            Visulations.export_TLE_intial_position(states,selectedType);
        } catch (Exception e) {
            System.err.println("Erreur dans propagation statecraft : " + e.getMessage());
            e.printStackTrace();
        }
        scanner.close();
    }



    /**
     * Affiche tous les types de TLE disponibles
     */
    public static void displayAvailableTLETypes() {
        System.out.println("=== Types de TLE disponibles sur Celestrak ===\n");
        for (int i = 0; i < TLEType.values().length; i++) {
            TLEType type = TLEType.values()[i];
            System.out.printf("%2d. %-20s - %s%n", i + 1, type.name(), type.getDescription());
        }
        System.out.println();
    }

    /**
     * Télécharge les fichiers TLE du type spécifié
     */
    private static void downloadTLEFiles(TLEType tleType) throws Exception {
        System.out.println("Téléchargement des fichiers TLE (" + tleType.getDescription() + ")...");

        // Créer le répertoire s'il n'existe pas
        Files.createDirectories(Paths.get(TLE_DATA_DIR));

        String outputFile = TLE_DATA_DIR + File.separator + tleType.getFilename()+format;

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tleType.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Files.writeString(Paths.get(outputFile), response.body());
                System.out.println("✓ Fichier TLE téléchargé : " + outputFile);
            } else {
                throw new IOException("HTTP " + response.statusCode() +
                        " lors du téléchargement");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Erreur lors du téléchargement : " +
                    e.getMessage());
            throw new Exception(e);
        }
    }

    private static List<SpacecraftState> create_OREKIT_Statecraft_state(String filename) throws IOException {
        List<TLE> tleList = new ArrayList<>();
        List<SpacecraftState> states = new ArrayList<>();
        String final_filename = "src\\main\\java\\com\\example\\TLE\\" + filename + format;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(final_filename))) {
            String line;
            String objectName = null;
            String tleLine1 = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }
                
                // Check if this is a TLE line (starts with 1 or 2)
                if (line.startsWith("1 ") || line.startsWith("2 ")) {
                    if (line.startsWith("1 ")) {
                        // First line of TLE
                        tleLine1 = line;
                    } else if (line.startsWith("2 ") && tleLine1 != null) {
                        // Second line of TLE 
                        String tleLine2 = line;
                        
                        try {
                            // Create TLE from the two-line element set
                            // Orekit's TLE constructor takes the two lines as strings
                            TLE tle = new TLE(tleLine1, tleLine2);
                            
                            tleList.add(tle);
                            states.add(TLEPropagator.selectExtrapolator(tle).getInitialState());
                            System.out.println(" TLE #" + tle.getSatelliteNumber() + " (" + objectName + ") chargé");
                            
                            // Reset for next satellite
                            tleLine1 = null;
                            objectName = null;
                        } catch (Exception e) {
                            System.err.println("Erreur création TLE: " + e.getMessage());
                            System.err.println("   Ligne 1: " + tleLine1);
                            System.err.println("   Ligne 2: " + tleLine2);
                            tleLine1 = null;
                            objectName = null;
                        }
                    }
                } else {
                    // This is the satellite name (appears before TLE lines)
                    objectName = line;
                }
            }
        }
        
        System.out.println("\n Total: " + tleList.size() + " TLE chargés depuis: " + filename);
        return states;
    }

}