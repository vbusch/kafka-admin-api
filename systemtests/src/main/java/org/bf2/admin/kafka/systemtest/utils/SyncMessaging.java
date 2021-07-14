package org.bf2.admin.kafka.systemtest.utils;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SyncMessaging {
    protected static final Logger LOGGER = LogManager.getLogger(SyncMessaging.class);

    public static List<String> createConsumerGroups(Vertx vertx, AdminClient kafkaClient, int count, String bootstrap, VertxTestContext testContext, String token) throws Exception {
        Promise<List<String>> promise = Promise.promise();
        List<String> groupIds = Collections.synchronizedList(new ArrayList<>());

        List<NewTopic> newTopics = IntStream.range(0, count)
            .mapToObj(index -> {
                String topicName = UUID.randomUUID().toString();
                return new NewTopic(topicName, 1, (short) 1);
            })
            .collect(Collectors.toList());

        kafkaClient.createTopics(newTopics)
            .all()
            .whenComplete((nothing, error) -> {
                if (error != null) {
                    testContext.failNow(error);
                } else {
                    newTopics.stream()
                        .parallel()
                        .forEach(topic -> {
                            String groupName = UUID.randomUUID().toString();
                            groupIds.add(groupName);

                            Properties props;

                            if (token != null) {
                                props = ClientsConfig.getConsumerConfigOauth(bootstrap, groupName, token);
                            } else {
                                props = ClientsConfig.getConsumerConfig(bootstrap, groupName);
                            }

                            try (var consumer = new KafkaConsumer<>(props)) {
                                consumer.subscribe(List.of(topic.name()));
                                consumer.poll(Duration.ofSeconds(1));
                            }

                            LOGGER.info("Created group {} on topic {}", groupName, topic.name());
                        });
                }
            }).whenComplete((nothing, error) -> {
                if (error != null) {
                    promise.fail(error);
                } else {
                    promise.complete(groupIds);
                }
            });

        return promise.future()
                .toCompletionStage()
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
    }

    public static List<String> createConsumerGroups(Vertx vertx, AdminClient kafkaClient, int count, String bootstrap, VertxTestContext testContext) throws Exception {
        return createConsumerGroups(vertx, kafkaClient, count, bootstrap, testContext, null);
    }

    public static KafkaProducer<String, String> createProducer(String bootstrap) {
        final KafkaProducer<String, String> producer = new KafkaProducer<String, String>(ClientsConfig.getProducerConfig(bootstrap));
        return producer;
    }
}
