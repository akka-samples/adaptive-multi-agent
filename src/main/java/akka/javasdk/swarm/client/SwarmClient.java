/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm.client;

import akka.annotation.DoNotInherit;

/**
 * Client for selecting a swarm session. Returned by {@code componentClient.forSwarm()}.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public interface SwarmClient {

  /**
   * The swarm participates in a session, which is used for conversational memory
   * shared across agents within the swarm.
   */
  SwarmClientInSession inSession(String sessionId);
}
