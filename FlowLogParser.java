import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FlowLogParser {
    private static final String IANA_PROTOCOL_NUMBERS = "asset/protocol_numbers_adapted.csv"; // static source file, please ensure path correctness
    private final Map<String, String> protocolMap = new HashMap<>(); // IANA protocol number -> protocol keyword
    private final Map<Key, String> lookupTable = new HashMap<>(); // port-protocol combination key -> tag
    private final Map<String, Integer> tagCounts = new HashMap<>(); // counting map of tag occurrences
    private final Map<Key, Integer> portProtocolCounts = new HashMap<>(); // counting map of port-protocol combinations

    // Flyweight Key as Record
    private record Key(int port, String protocol){}

    // Flyweight Factory with thread-safe pool
    private static class KeyFactory {
        // ConcurrentHashMap not necessary here, used by convention
        private static final Map<String, Key> pool = new ConcurrentHashMap<>();

        public static Key getKey(int port, String protocol) {
            String compositeKey = port + "|" + protocol;  // Unique identifier
            return pool.computeIfAbsent(compositeKey, k -> new Key(port, protocol));
        }
    }

    /**
     * Processes the (static) protocol numbers file and populates the protocolMap.
     *
     * @param filename the path to the protocol numbers file
     */
    public void processProtocolNumbers(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                // Skip special/truncated lines
                if (parts.length >= 2 && parts[0].matches("\\d+") && !parts[1].isEmpty()) {
                    String protocol = parts[1].trim().toLowerCase();
                    protocolMap.put(parts[0].trim(), protocol);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading protocol numbers: " + e.getMessage());
            System.err.println("Please check the static protocol table is at the correct location.");
        }
    }

    /**
     * Processes the lookup table file and populates the lookupTable.
     *
     * @param filename the path to the lookup table file
     */
    public void processLookupTable(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    int port = Integer.parseInt(parts[0].trim());
                    String protocol = parts[1].trim().toLowerCase();
                    Key key = KeyFactory.getKey(port, protocol);
                    lookupTable.put(key, parts[2].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading lookup table: " + e.getMessage());
        }
    }

    /**
     * Parses the flow log file and updates the counts of tags and port/protocol combinations.
     *
     * @param filename the path to the flow log file
     */
    public void parseFlowLog(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.lines()
                    //.parallel() // Could improve performance when files are large enough, require synchronized hashmaps
                    .forEach(line -> {
                String[] parts = line.split("\\s+");
                if (parts.length >= 8) {
                    int dstPort = Integer.parseInt(parts[6]);

                    String protocolKeyword = protocolMap.getOrDefault(parts[7], "unknown");
                    Key key = KeyFactory.getKey(dstPort, protocolKeyword);

                    // Update counts
                    portProtocolCounts.merge(key, 1, Integer::sum);

                    // Update tag counts
                    String tag = lookupTable.getOrDefault(key, "Untagged");
                    tagCounts.merge(tag, 1, Integer::sum);
                }
            });
        } catch (IOException e) {
            System.err.println("Error reading flow log: " + e.getMessage());
        }
    }

    /**
     * Writes the output to a specified file, including tag counts and port/protocol combination counts.
     *
     * @param outputFile the path to the output file
     */
    public void writeOutput(String outputFile) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write tag counts
            writer.println("Tag Counts:");
            tagCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> writer.println(e.getKey() + "," + e.getValue()));

            // Write port/protocol counts (sorted by port)
            writer.println("\nPort/Protocol Combination Counts:");
            portProtocolCounts.entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> e.getKey().port()))
                    .forEach(e -> writer.println(
                            e.getKey().port() + "," +
                                    e.getKey().protocol() + "," +
                                    e.getValue()
                    ));
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Source file names as program argument
        final String LookupTable = args[0];
        final String FlowLog = args[1];
        final String OutputFile = "result/" + (args.length >= 3 ? args[2] : new Date().getTime() + ".log");

        long startTime, endTime;
        startTime = System.currentTimeMillis();
        FlowLogParser parser = new FlowLogParser();
        parser.processProtocolNumbers(IANA_PROTOCOL_NUMBERS);
        parser.processLookupTable(LookupTable);
        parser.parseFlowLog(FlowLog);
        endTime = System.currentTimeMillis();

        parser.writeOutput(OutputFile);
        System.out.printf("Parsed %s in %.2f seconds%n", FlowLog, (endTime - startTime) / 1000.0);
        System.out.printf("Result saved to %s\n", OutputFile);
    }
}