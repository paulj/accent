/**
 * Copyright (C) 2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.accent;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.tools.Tracer;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Smoke tests executed against real RabbitMQ instances.
 */
public class ConnectionSmokeTest {
  @Before
  public void ensureServer() throws IOException {
    AccentTestUtils.ensureAMQPServer();
  }

  @Test
  public void shouldConnectPublishAndConsume() throws IOException, InterruptedException {
    AccentConnection conn = new AccentConnection(new ConnectionFactory(), AccentTestUtils.printingFailureLogger());
    try {
      AccentChannel ch = conn.createChannel();

      // Add a setup listener to create the queue
      ch.addChannelSetupListener(new ChannelListenerAdapter() {
        @Override public void channelCreated(Channel c) throws IOException {
          c.queueDeclare("accent.testq", false, false, true, null);
        }
      });

      QueueingConsumer consumer = new QueueingConsumer(null);
      AccentConsumer aConsumer = new AccentConsumer(ch, "accent.testq", consumer);

      AccentConfirmPublisher publisher = new AccentConfirmPublisher(ch);
      publisher.reliablePublish("", "accent.testq", new AMQP.BasicProperties(), new byte[0]);

      QueueingConsumer.Delivery d = consumer.nextDelivery(10000);

      assertNotNull(d);
      assertEquals(0, d.getBody().length);

      aConsumer.reliableAck(d.getEnvelope().getDeliveryTag(), false);



      aConsumer.close();
    } finally {
      conn.close();
    }
  }

  @Test
  public void shouldConsumeEvenIfConnectionIsntCreatedUntilLater() throws IOException, InterruptedException {
    // Create a connection and deliver a message
    ConnectionFactory directFactory = new ConnectionFactory();
    ConnectionFactory managedFactory = new ConnectionFactory();
    managedFactory.setPort(5673);

    AccentConnection directConn = new AccentConnection(directFactory, AccentTestUtils.printingFailureLogger());
    AccentConnection managedConn = new AccentConnection(managedFactory, AccentTestUtils.smallPrintingFailureLogger());
    try {
      AccentChannel directCh = directConn.createChannel();
      AccentChannel managedCh = managedConn.createChannel();

      // Add a setup listener to create the queue
      directCh.addChannelSetupListener(new ChannelListenerAdapter() {
        @Override
        public void channelCreated(Channel c) throws IOException {
          c.queueDeclare("accent.testq", false, false, true, null);
        }
      });

      // Consumer on the managed channel which we'll allow to work later
      QueueingConsumer consumer = new QueueingConsumer(null);
      AccentConsumer aConsumer = new AccentConsumer(managedCh, "accent.testq", consumer);

      // Publish the message on a direct channel
      AccentConfirmPublisher publisher = new AccentConfirmPublisher(directCh);
      publisher.reliablePublish("", "accent.testq", new AMQP.BasicProperties(), new byte[0]);

      // Try to consume. We shouldn't get anything because we haven't allowed the connection through
      QueueingConsumer.Delivery d = consumer.nextDelivery(2000);
      assertNull(d);

      // Start the tracer to give us a valid connection
      ConnectionProxy proxy = createProxy(5673);
      try {
        QueueingConsumer.Delivery d2 = consumer.nextDelivery(2000);
        assertNotNull(d2);
        assertEquals(0, d2.getBody().length);

        aConsumer.reliableAck(d2.getEnvelope().getDeliveryTag(), false);

        aConsumer.close();
      } finally {
        proxy.close();
      }
    } finally {
      managedConn.close();
    }
  }

  private static ConnectionProxy createProxy(int port) throws IOException {
    return new ConnectionProxy(port);
  }

  private static class ConnectionProxy implements Closeable, Tracer.Logger {
    private final int listenPort;
    private Tracer tracer;

    private ConnectionProxy(int listenPort) throws IOException {
      this.listenPort = listenPort;
      this.tracer = new Tracer(listenPort, "proxy", "localhost", ConnectionFactory.DEFAULT_AMQP_PORT, this, new Properties());
      this.tracer.start();
    }

    @Override
    public void close() throws IOException {
    }


    //
    // Tracer Logger Implementation
    //


    @Override
    public boolean start() {
      return true;
    }

    @Override
    public boolean stop() {
      return true;
    }

    @Override
    public void log(String msg) {
    }
  }
}
