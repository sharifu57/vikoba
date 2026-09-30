package vikoba.service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic smsTopic() {
        return TopicBuilder.name("vikoba.sms").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic smsRetryTopic() {
        return TopicBuilder.name("vikoba.sms.retry").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic smsDlqTopic() {
        return TopicBuilder.name("vikoba.sms.dlq").partitions(1).replicas(1).build();
    }
}
