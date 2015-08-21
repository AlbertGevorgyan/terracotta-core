/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.objectserver.handler;

import com.tc.async.api.AbstractEventHandler;
import com.tc.async.api.ConfigurationContext;
import com.tc.net.groups.TCGroupHandshakeMessage;
import com.tc.net.groups.TCGroupManagerImpl;

public class TCGroupHandshakeMessageHandler extends AbstractEventHandler<TCGroupHandshakeMessage> {
  private final TCGroupManagerImpl manager;
  
  public TCGroupHandshakeMessageHandler(TCGroupManagerImpl manager) {
    this.manager = manager;
  }
  
  @Override
  public void handleEvent(TCGroupHandshakeMessage msg) {
    manager.receivedHandshake(msg);
  }

  @Override
  public void initialize(ConfigurationContext context) {
    super.initialize(context);
  }

}