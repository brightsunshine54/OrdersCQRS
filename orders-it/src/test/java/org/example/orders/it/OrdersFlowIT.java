package org.example.orders.it;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.orders.client.api.OrdersApi;
import org.example.orders.client.invoker.ApiClient;
import org.example.orders.client.model.SearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сквозной тест: Kafka + OpenSearch в Docker (Testcontainers),
 * оба сервиса запущены как отдельные процессы из собранных jar,
 * сообщения отправляются в Kafka, поиск — через сгенерированный Feign-клиент.
 * Запускается на фазе integration-test (mvn verify), без Docker скипается.
 */
class OrdersFlowIT {

    private static final String TOPIC = "orders-snapshots";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration INDEXING_TIMEOUT = Duration.ofSeconds(120);

    private static ConfluentKafkaContainer kafka;
    private static GenericContainer<?> opensearch;
    private static Process indexerProcess;
    private static Process searchProcess;
    private static OrdersApi ordersApi;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker недоступен — интеграционный тест пропущен");

        // cp-kafka > 7.8.0 несовместим с testcontainers (testcontainers-java#10093)
        kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));
        kafka.start();

        opensearch = new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.19.0"))
                .withEnv("discovery.type", "single-node")
                .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms1g -Xmx1g")
                .withExposedPorts(9200)
                .waitingFor(Wait.forHttp("/").forPort(9200).withStartupTimeout(STARTUP_TIMEOUT));
        opensearch.start();

        String opensearchUrl = "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
        indexerProcess = startService("indexer", System.getProperty("indexer.jar"), Map.of(
                "KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers(),
                "OPENSEARCH_HOST", opensearchUrl));

        int searchPort = freePort();
        searchProcess = startService("search", System.getProperty("search.jar"), Map.of(
                "OPENSEARCH_HOST", opensearchUrl,
                "SERVER_PORT", String.valueOf(searchPort)));

        waitForHttp("http://localhost:" + searchPort + "/v3/api-docs", STARTUP_TIMEOUT);
        ordersApi = new ApiClient()
                .setBasePath("http://localhost:" + searchPort)
                .buildClient(OrdersApi.class);
    }

    @AfterAll
    static void tearDown() {
        if (indexerProcess != null) {
            indexerProcess.destroyForcibly();
        }
        if (searchProcess != null) {
            searchProcess.destroyForcibly();
        }
        if (kafka != null) {
            kafka.stop();
        }
        if (opensearch != null) {
            opensearch.stop();
        }
    }

    @Test
    void orderSnapshotsFlowFromKafkaToSearch() throws Exception {
        produce(
                orderJson("it-1", "cust-42", "PAID", "Книга по Java"),
                orderJson("it-2", "cust-42", "PAID", "Кружка"),
                orderJson("it-3", "cust-7", "NEW", "Книга по Kafka"));

        SearchResult paid = awaitSearch(
                () -> ordersApi.search(null, "PAID", null, null, null, null, null, null, null), 2);
        assertThat(paid.getOrders())
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo("PAID"));

        awaitSearch(() -> ordersApi.search(null, null, "cust-42", null, null, null, null, null, null), 2);
        awaitSearch(() -> ordersApi.search(null, null, null, null, null, null, "Книга", null, null), 2);

        SearchResult byId = awaitSearch(
                () -> ordersApi.search("it-1", null, null, null, null, null, null, null, null), 1);
        assertThat(byId.getOrders())
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.getOrderId()).isEqualTo("it-1");
                    assertThat(order.getCustomerId()).isEqualTo("cust-42");
                });
    }

    private static void produce(String... messages) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (String message : messages) {
                producer.send(new ProducerRecord<>(TOPIC, message));
            }
            producer.flush();
        }
    }

    private static String orderJson(String orderId, String customerId, String status, String itemName) {
        return """
                {"orderId":"%s","customerId":"%s","status":"%s","totalAmount":1290.50,"currency":"RUB",
                 "items":[{"productId":"p-1","name":"%s","quantity":1,"price":1290.50}],
                 "updatedAt":"%s","attributes":{"source":"orders-it"}}
                """.formatted(orderId, customerId, status, itemName, Instant.now());
    }

    private static SearchResult awaitSearch(Callable<SearchResult> call, long expectedTotal) throws Exception {
        long deadline = System.nanoTime() + INDEXING_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                SearchResult result = call.call();
                if (result.getTotal() != null && result.getTotal() >= expectedTotal) {
                    return result;
                }
            } catch (Exception ignored) {
                // сервис ещё поднимается или документы не доехали — ждём
            }
            Thread.sleep(2000);
        }
        throw new AssertionError("Не дождались total >= " + expectedTotal
                + " за " + INDEXING_TIMEOUT.toSeconds() + "с (логи сервисов: target/it-logs/)");
    }

    private static Process startService(String name, String jar, Map<String, String> env) throws IOException {
        Path logDir = Path.of("target/it-logs");
        Files.createDirectories(logDir);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder process = new ProcessBuilder(java, "-jar", jar);
        process.environment().putAll(env);
        process.redirectErrorStream(true);
        process.redirectOutput(logDir.resolve(name + ".log").toFile());
        return process.start();
    }

    private static void waitForHttp(String url, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // сервис ещё не поднялся
            }
            Thread.sleep(2000);
        }
        throw new AssertionError("Сервис не ответил 200 на " + url + " за " + timeout.toSeconds() + "с");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
