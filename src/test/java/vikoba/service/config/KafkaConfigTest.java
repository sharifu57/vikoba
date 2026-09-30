package vikoba.service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {
    @Test
    void registersTopicAdministrationAndListenerInfrastructure() throws Exception {
        ResourcePropertySource properties = new ResourcePropertySource(new ClassPathResource("application.properties"));
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(properties))
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withUserConfiguration(KafkaConfig.class)
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.admin.auto-create=false",
                        "spring.kafka.listener.auto-startup=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaAdmin.class);
                    assertThat(context).hasSingleBean(KafkaListenerEndpointRegistry.class);
                    assertThat(context).hasBean("kafkaListenerContainerFactory");
                    assertThat(context.getBean(ProducerFactory.class).getConfigurationProperties())
                            .containsEntry("max.block.ms", "5000")
                            .containsEntry("acks", "all");
                    assertThat(context.getEnvironment().getProperty("spring.kafka.admin.fail-fast"))
                            .isEqualTo("false");
                    assertThat(context.getBeansOfType(NewTopic.class).values())
                            .extracting(NewTopic::name)
                            .containsExactlyInAnyOrder("vikoba.sms", "vikoba.sms.retry", "vikoba.sms.dlq");
                });
    }
}
