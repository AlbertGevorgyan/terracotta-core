/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.objectserver.core.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.terracotta.monitoring.IMonitoringProducer;
import org.terracotta.monitoring.PlatformClientFetchedEntity;
import org.terracotta.monitoring.PlatformConnectedClient;
import org.terracotta.monitoring.PlatformEntity;
import org.terracotta.monitoring.PlatformMonitoringConstants;

import com.tc.l2.state.StateManager;
import com.tc.net.ClientID;
import com.tc.net.TCSocketAddress;
import com.tc.net.protocol.tcm.MessageChannel;
import com.tc.object.EntityID;
import com.tc.objectserver.core.api.ITopologyEventCollector;
import com.tc.util.Assert;
import com.tc.util.State;


/**
 * The basic implementation of ITopologyEventCollector which ensures that events aren't obviously incorrect by ensuring
 * consistent state and symmetry of events.
 */
public class ManagementTopologyEventCollector implements ITopologyEventCollector {
  // Note that serviceInterface may be null if there isn't an IMonitoringProducer service registered.
  private final IMonitoringProducer serviceInterface;
  private final Set<ClientID> connectedClients;
  private final Set<EntityID> entities;
  private final Map<FetchTuple, Integer> fetchPairCounts;
  private boolean isActiveState;

  public ManagementTopologyEventCollector(IMonitoringProducer serviceInterface) {
    this.serviceInterface = serviceInterface;
    this.connectedClients = new HashSet<ClientID>();
    this.entities = new HashSet<EntityID>();
    this.fetchPairCounts = new HashMap<FetchTuple, Integer>();
    this.isActiveState = false;
    
    // Do our initial configuration of the service.
    if (null != this.serviceInterface) {
      // Create the root of the platform tree.
      this.serviceInterface.addNode(new String[0], PlatformMonitoringConstants.PLATFORM_ROOT_NAME, null);
      // Create the root of the client subtree.
      this.serviceInterface.addNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.CLIENTS_ROOT_NAME, null);
      // Create the root of the entity subtree.
      this.serviceInterface.addNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.ENTITIES_ROOT_NAME, null);
      // Create the root of the client-entity fetch subtree.
      this.serviceInterface.addNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.FETCHED_ROOT_NAME, null);
      // Create the initial server state.
      this.serviceInterface.addNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.STATE_NODE_NAME, PlatformMonitoringConstants.SERVER_STATE_STOPPED);
    }
  }

  @Override
  public synchronized void serverDidEnterState(State state) {
    // We track whether or not we are in an active state to ensure that entities are created/loaded in the expected state.
    this.isActiveState = (StateManager.ACTIVE_COORDINATOR == state);
    
    // Set this in the monitoring interface.
    if (null != this.serviceInterface) {
      String stateValue = this.isActiveState ? PlatformMonitoringConstants.SERVER_STATE_ACTIVE : PlatformMonitoringConstants.SERVER_STATE_PASSIVE;
      this.serviceInterface.removeNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.STATE_NODE_NAME);
      this.serviceInterface.addNode(PlatformMonitoringConstants.PLATFORM_PATH, PlatformMonitoringConstants.STATE_NODE_NAME, stateValue);
    }
  }

  @Override
  public synchronized void clientDidConnect(MessageChannel channel, ClientID client) {
    // Ensure that this client isn't already connected.
    Assert.assertFalse(this.connectedClients.contains(client));
    // Now, add it to the connected set.
    this.connectedClients.add(client);
    
    // Add it to the monitoring interface.
    if (null != this.serviceInterface) {
      // Create the structure to describe this client.
      TCSocketAddress localAddress = channel.getLocalAddress();
      TCSocketAddress remoteAddress = channel.getRemoteAddress();
      PlatformConnectedClient clientDescription = new PlatformConnectedClient(localAddress.getAddress(), localAddress.getPort(), remoteAddress.getAddress(), remoteAddress.getPort());
      // We will use the ClientID long value as the node name.
      String nodeName = clientIdentifierForService(client);
      this.serviceInterface.addNode(PlatformMonitoringConstants.CLIENTS_PATH, nodeName, clientDescription);
    }
  }

  @Override
  public synchronized void clientDidDisconnect(MessageChannel channel, ClientID client) {
    // Ensure that this client was already connected.
    Assert.assertTrue(this.connectedClients.contains(client));
    // Now, remove it from the connected set.
    this.connectedClients.remove(client);
    
    // Remove it from the monitoring interface.
    if (null != this.serviceInterface) {
      String nodeName = clientIdentifierForService(client);
      this.serviceInterface.removeNode(PlatformMonitoringConstants.CLIENTS_PATH, nodeName);
    }
  }

  @Override
  public synchronized void entityWasCreated(EntityID id, boolean isActive) {
    // Ensure that this is the expected state.
    Assert.assertTrue(isActive == this.isActiveState);
    // Ensure that this entity didn't already exist.
    Assert.assertFalse(this.entities.contains(id));
    addEntityToTracking(id, isActive);
  }

  @Override
  public synchronized void entityWasDestroyed(EntityID id) {
    // Ensure that this entity already exists.
    Assert.assertTrue(this.entities.contains(id));
    // Now, remove it from the set.
    removeEntityFromTracking(id);
  }

  @Override
  public synchronized void entityWasReloaded(EntityID id, boolean isActive) {
    // Ensure that this is the expected state.
    // NOTE:  We currently can't verify the isActiveState since the reload path sets it _after_ the entities are reloaded.
    // Note that this could happen due to promotion or reloading from restart so we can't know if it already is in our set.
    if (!this.entities.contains(id)) {
      // Seems to be new so add it to the set.
      addEntityToTracking(id, isActive);
    }
  }

  @Override
  public synchronized void clientDidFetchEntity(ClientID client, EntityID id) {
    // XXX: Note that there is currently no handling of reconnect on entity promotion from passive to active so there may
    // be double-counting, here.
    FetchTuple tuple = new FetchTuple(client, id);
    int count = 0;
    if (this.fetchPairCounts.containsKey(tuple)) {
      count = this.fetchPairCounts.get(tuple);
    }
    count += 1;
    this.fetchPairCounts.put(tuple, count);
    
    // Add it to the monitoring interface.
    if (null != this.serviceInterface) {
      String clientIdentifier = clientIdentifierForService(client);
      String entityIdentifier = entityIdentifierForService(id);
      PlatformClientFetchedEntity record = new PlatformClientFetchedEntity(clientIdentifier, entityIdentifier);
      String fetchIdentifier = fetchIdentifierForService(clientIdentifier, entityIdentifier);
      this.serviceInterface.addNode(PlatformMonitoringConstants.FETCHED_PATH, fetchIdentifier, record);
    }
  }

  @Override
  public synchronized void clientDidReleaseEntity(ClientID client, EntityID id) {
    FetchTuple tuple = new FetchTuple(client, id);
    // We can't release something we didn't fetch.
    Assert.assertTrue(this.fetchPairCounts.containsKey(tuple));
    int count = this.fetchPairCounts.remove(tuple);
    count -= 1;
    if (count > 0) {
      this.fetchPairCounts.put(tuple, count);
    }
    
    // Remove it from the monitoring interface.
    if (null != this.serviceInterface) {
      String clientIdentifier = clientIdentifierForService(client);
      String entityIdentifier = entityIdentifierForService(id);
      String fetchIdentifier = fetchIdentifierForService(clientIdentifier, entityIdentifier);
      this.serviceInterface.removeNode(PlatformMonitoringConstants.FETCHED_PATH, fetchIdentifier);
    }
  }
  
  
  private static class FetchTuple {
    private final ClientID client;
    private final EntityID entity;
    
    public FetchTuple(ClientID client, EntityID entity) {
      this.client = client;
      this.entity = entity;
    }
    
    @Override
    public int hashCode() {
      return this.client.hashCode() ^ 
          (this.entity.hashCode() << 1);
    }
    
    @Override
    public boolean equals(Object obj) {
      boolean isEqual = false;
      if (obj instanceof FetchTuple) {
        FetchTuple other = (FetchTuple) obj;
        isEqual = this.client.equals(other.client)
            && this.entity.equals(other.entity);
      }
      return isEqual;
    }
  }


  private void addEntityToTracking(EntityID id, boolean isActive) {
    this.entities.add(id);
    
    // Add it to the monitoring interface.
    if (null != this.serviceInterface) {
      String entityClassName = id.getClassName();
      String entityName = id.getEntityName();
      PlatformEntity record = new PlatformEntity(entityClassName, entityName, isActive);
      String entityIdentifier = entityIdentifierForService(id);
      this.serviceInterface.addNode(PlatformMonitoringConstants.ENTITIES_PATH, entityIdentifier, record);
    }
  }

  private void removeEntityFromTracking(EntityID id) {
    this.entities.remove(id);
    
    // Remove it to the monitoring interface.
    if (null != this.serviceInterface) {
      String entityIdentifier = entityIdentifierForService(id);
      this.serviceInterface.removeNode(PlatformMonitoringConstants.ENTITIES_PATH, entityIdentifier);
    }
  }

  private String clientIdentifierForService(ClientID id) {
    return "" + id.toLong();
  }

  private String entityIdentifierForService(EntityID id) {
    return id.getClassName() + id.getEntityName();
  }

  private String fetchIdentifierForService(String clientIdentifier, String entityIdentifier) {
    return clientIdentifier + entityIdentifier;
  }
}
