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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

//  @Test
//  public void shouldConsumeEvenIfConnectionIsntCreatedUntilLater() {
//
//  }
}
