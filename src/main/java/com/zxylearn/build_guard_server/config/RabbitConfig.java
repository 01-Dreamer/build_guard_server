package com.zxylearn.build_guard_server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
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

    @Bean
    TopicExchange deviceExchange() {
        return new TopicExchange(MqNames.DEVICE_EXCHANGE, true, false);
    }

    @Bean
    Queue deviceTelemetryQueue() {
        return new Queue(MqNames.DEVICE_TELEMETRY_QUEUE, true);
    }

    @Bean
    Queue deviceStatusQueue() {
        return new Queue(MqNames.DEVICE_STATUS_QUEUE, true);
    }

    @Bean
    Queue cameraFrameQueue() {
        return new Queue(MqNames.CAMERA_FRAME_QUEUE, true);
    }

    @Bean
    Binding deviceTelemetryBinding(Queue deviceTelemetryQueue, TopicExchange deviceExchange) {
        return BindingBuilder.bind(deviceTelemetryQueue).to(deviceExchange).with(MqNames.DEVICE_TELEMETRY_ROUTING_KEY);
    }

    @Bean
    Binding deviceStatusBinding(Queue deviceStatusQueue, TopicExchange deviceExchange) {
        return BindingBuilder.bind(deviceStatusQueue).to(deviceExchange).with(MqNames.DEVICE_STATUS_ROUTING_KEY);
    }

    @Bean
    Binding cameraFrameBinding(Queue cameraFrameQueue, TopicExchange deviceExchange) {
        return BindingBuilder.bind(cameraFrameQueue).to(deviceExchange).with(MqNames.CAMERA_FRAME_ROUTING_KEY);
    }
}
