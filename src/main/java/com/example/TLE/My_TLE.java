package com.example.TLE;
import com.example.View.Visulations;
import com.example.View.Visulations.*;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

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

    // Types de TLE disponibles sur Celestrak
    public enum TLEType {
        ACTIVE("GROUP=active", "active.csv", "Satellites actifs"),
        LAST_30_DAYS("GROUP=last-30-days", "last-30-days.csv", "Satellites lancés dans les 30 derniers jours"),
        WEATHER("GROUP=weather", "weather.csv", "Satellites météorologiques"),
        STATIONS("GROUP=stations", "stations.csv", "Stations spatiales"),
        HYPERBOLIC("GROUP=hyperbolic", "hyperbolic.csv", "Satellites hyperboliques"),
        GEO_INACTIVE("GROUP=geo-inactive", "geo-inactive.csv", "Géostationnaires inactifs"),
        SPACEX("GROUP=starlink", "starlink.csv", "Constellation Starlink"),
        DECAYING("SPECIAL=DECAYING", "decaying.csv", "Satellites en décroissance");

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
            return CELESTRAK_BASE + "?" + query + "&FORMAT=csv";
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

        System.out.print("Choisissez le numéro du type de TLE: ");
        int choice = scanner.nextInt();

        // Vérifier que le choix est valide
        My_TLE.TLEType[] types = My_TLE.TLEType.values();
        do {
            System.out.print("Entrez un numéro valide (1-" + types.length + "): ");
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
            My_TLE.check_if_download_tle(selectedType);
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
            create_Statecraft_position(states);
        } catch (Exception e) {
            System.err.println("Erreur dans propagation statecraft : " + e.getMessage());
            e.printStackTrace();
        }
        scanner.close();
    }


    private static void create_Statecraft_position( List<SpacecraftState> states) {
        int i=1;
        for (SpacecraftState state : states) {
            Visulations.export_TLE_intial_position(state,i);
            i+=1;
        }
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

        String outputFile = TLE_DATA_DIR + File.separator + tleType.getFilename();

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

    private static List<SpacecraftState > create_OREKIT_Statecraft_state(String filename) throws IOException {
        List<TLE> tleList = new ArrayList<>();
        List<SpacecraftState> states = new ArrayList<>();
        String final_filename = "src\\main\\java\\com\\example\\TLE\\"+filename;
        try (BufferedReader reader = new BufferedReader(new FileReader(final_filename))) {
            String line;

            // Skip header
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",");

                if (parts.length < 17) {
                    System.err.println(" Colonnes insuffisantes (trouvé " + parts.length + "): " + line);
                    continue;
                }

                try {
                    // Parser les colonnes selon le format CSV fourni
                    String objectName = parts[0].trim(); // OBJECT_NAME
                    String objectId = parts[1].trim(); // OBJECT_ID

                    // Parser l'époque (format: YYYY-MM-DDTHH:mm:ss.SSSSSS)
                    String epochStr = parts[2].trim();
                    AbsoluteDate epoch = new AbsoluteDate(epochStr, TimeScalesFactory.getUTC());

                    // Paramètres orbitaux
                    double meanMotion = Double.parseDouble(parts[3].trim()); // rev/jour -> conversion nécessaire
                    double e = Double.parseDouble(parts[4].trim()); // excentricité
                    double i = Math.toRadians(Double.parseDouble(parts[5].trim())); // inclinaison (convertir deg->rad)
                    double raan = Math.toRadians(Double.parseDouble(parts[6].trim())); // RA of ascending node
                                                                                       // (deg->rad)
                    double pa = Math.toRadians(Double.parseDouble(parts[7].trim())); // arg of perigee (deg->rad)
                    double meanAnomaly = Math.toRadians(Double.parseDouble(parts[8].trim())); // mean anomaly (deg->rad)

                    int ephemerisType = Integer.parseInt(parts[9].trim()); // EPHEMERIS_TYPE
                    char classification = parts[10].trim().charAt(0); // CLASSIFICATION_TYPE
                    int satelliteNumber = Integer.parseInt(parts[11].trim()); // NORAD_CAT_ID
                    int elementNumber = Integer.parseInt(parts[12].trim()); // ELEMENT_SET_NO
                    int revolutionNumberAtEpoch = Integer.parseInt(parts[13].trim()); // REV_AT_EPOCH
                    double bStar = parseBStar(parts[14].trim()); // BSTAR (format scientifique)
                    double meanMotionFirstDerivative = Double.parseDouble(parts[15].trim()); // MEAN_MOTION_DOT
                    double meanMotionSecondDerivative = Double.parseDouble(parts[16].trim()); // MEAN_MOTION_DDOT

                    // Convertir MEAN_MOTION de rev/jour en rad/s
                    double meanMotionRadPerSec = meanMotion * 2 * Math.PI / 86400.0;

                    // Extraire année et numéro de lancement de OBJECT_ID (format: YYYY-NNNX), attention au nombre de lettre a la fin qui est variable
                    // Ex: 2019-074B -> year=2019, launchNumber=74, launchPiece=B
                    String[] idParts = objectId.split("-");
                    int launchYear = Integer.parseInt(idParts[0]);
                    
                    long letterCount = idParts[1].chars()
                        .filter(Character::isLetter)
                        .count();
                        int launchNumber = Integer.parseInt(idParts[1].substring(0, idParts[1].length() -  (int) letterCount));

                        String launchPiece = idParts[1].substring(idParts[1].length() - (int) letterCount);

                    // Créer le TLE avec le constructeur complet
                    TLE tle = new TLE(satelliteNumber, classification, launchYear, launchNumber,
                            launchPiece, ephemerisType, elementNumber, epoch,
                            meanMotionRadPerSec, meanMotionFirstDerivative, meanMotionSecondDerivative,
                            e, i, pa, raan, meanAnomaly, revolutionNumberAtEpoch, bStar);
                    
                    tleList.add(tle);
                    states.add(TLEPropagator.selectExtrapolator(tle).getInitialState());
                    System.out.println(" TLE #" + satelliteNumber + " (" + objectName + ") chargé");

                } catch (NumberFormatException e) {
                    System.err.println("Erreur parsing numérique: " + e.getMessage());
                    System.err.println("   Ligne: " + line);
                } catch (Exception e) {
                    System.err.println("Erreur création TLE: " + e.getMessage());
                    System.err.println("   Ligne: " + line);
                }
            }
        }

        System.out.println("\n Total: " + tleList.size() + " TLE chargés depuis: " + filename);
        
        return states;
    }

    /**
     * Parser BSTAR au format scientifique (ex: .96250111E-4)
     */
    private static double parseBStar(String bstarStr) {
        // Format Celestrak: .96250111E-4
        // Convertir en format Java standard: 0.96250111E-4
        if (bstarStr.startsWith(".")) {
            bstarStr = "0" + bstarStr;
        }
        return Double.parseDouble(bstarStr);
    }
}