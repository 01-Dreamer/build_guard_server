package com.zxylearn.build_guard_server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    DirectExchange aiExchange() {
        return new DirectExchange(MqNames.AI_EXCHANGE, true, false);
    }

    @Bean
    Queue aiRequestQueue() {
        return new Queue(MqNames.AI_REQUEST_QUEUE, true);
    }

    @Bean
    Queue aiResultQueue() {
        return new Queue(MqNames.AI_RESULT_QUEUE, true);
    }

    @Bean
    Binding aiRequestBinding(Queue aiRequestQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(aiRequestQueue).to(aiExchange).with(MqNames.AI_REQUEST_ROUTING_KEY);
    }

    @Bean
    Binding aiResultBinding(Queue aiResultQueue, DirectExchange aiExchange) {
        return BindingBuilder.bind(aiResultQueue).to(aiExchange).with(MqNames.AI_RESULT_ROUTING_KEY);
    }
}
