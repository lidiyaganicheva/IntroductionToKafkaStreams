import com.google.gson.*;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RootDomainCounter {
    

    public static void main(String[] args) {
        Properties props = new Properties();
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "broker-1:19092");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "counter-" + UUID.randomUUID());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put("log4j.rootLogger", "ERROR, stdout");
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams");

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream("browser_history");

        KTable<String, Long> domainCounts = stream
                .mapValues(RootDomainCounter::extractRootDomain)
                .filter((key, domain) -> domain != null)
                .groupBy((key, domain) -> domain, Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("domain-counts"));

        // Periodic printing of top 5 root domains
        domainCounts.toStream().foreach((domain, count) -> TopDomains.update(domain, count));

        // Start printer thread
        new Thread(TopDomains::runPrinter).start();

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    // Extract root domain (.com, .org, etc.)
    private static String extractRootDomain(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String url = obj.get("url").getAsString();
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && host.contains(".")) {
                String[] parts = host.split("\\.");
                return parts[parts.length - 1]; // root domain
            }
        } catch (Exception e) {
            // Ignore malformed URLs
        }
        return null;
    }

    // Helper class to keep top 5 domains
    static class TopDomains {
        private static final Map<String, Long> counts = new HashMap<>();

        public static synchronized void update(String domain, Long count) {
            counts.put(domain, count);
        }

        public static void runPrinter() {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(10);
                    printTop5();
                } catch (InterruptedException ignored) {
                }
            }
        }

        private static synchronized void printTop5() {
            System.out.println("\nTop 5 root domains:");
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> System.out.printf("%s: %d\n", e.getKey(), e.getValue()));
        }
    }
}