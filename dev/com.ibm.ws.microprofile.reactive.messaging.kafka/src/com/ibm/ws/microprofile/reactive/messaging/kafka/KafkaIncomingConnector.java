/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.reactive.messaging.kafka;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import com.ibm.ws.microprofile.reactive.messaging.kafka.adapter.KafkaAdapterFactory;
import com.ibm.ws.microprofile.reactive.messaging.kafka.adapter.KafkaConsumer;

@Connector("io.openliberty.kafka")
@ApplicationScoped
public class KafkaIncomingConnector implements IncomingConnectorFactory {

    ManagedScheduledExecutorService executor;

    @Inject
    KafkaAdapterFactory kafkaAdapterFactory;

    public KafkaIncomingConnector() {
        Bundle b = FrameworkUtil.getBundle(KafkaIncomingConnector.class);
        ServiceReference<ManagedScheduledExecutorService> mgdSchedExecSvcRef = b.getBundleContext().getServiceReference(ManagedScheduledExecutorService.class);
        this.executor = b.getBundleContext().getService(mgdSchedExecSvcRef);
    }

    @PostConstruct
    private void validate() {
        if (this.executor == null) {
            throw new NullPointerException("no executor set");
        }
    }

    @Override
    public PublisherBuilder<Message<Object>> getPublisherBuilder(Config config) {

        // Extract our config
        List<String> topics = Arrays.asList(config.getValue("topics", String.class).split(" *, *", -1));
        int unackedLimit = config.getOptionalValue("unacked.limit", Integer.class).orElse(20);

        // Pass the rest of the config directly through to the kafkaConsumer
        Map<String, Object> consumerConfig = new HashMap<>(StreamSupport.stream(config.getPropertyNames().spliterator(),
                                                                                false).collect(Collectors.toMap(Function.identity(), (k) -> config.getValue(k, String.class))));

        // Set the config values which we hard-code
        consumerConfig.put("enable.auto.commit", "false"); // Connector handles commit in response to ack()
                                                           // automatically

        // Create the kafkaConsumer
        KafkaConsumer<String, Object> kafkaConsumer = this.kafkaAdapterFactory.newKafkaConsumer(consumerConfig);

        // Create our connector around the kafkaConsumer
        KafkaInput<String, Object> kafkaInput = new KafkaInput<>(this.kafkaAdapterFactory, kafkaConsumer, this.executor, topics, unackedLimit);

        return kafkaInput.getPublisher();
    }

}
