/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.stats;

import com.tc.management.AbstractTerracottaMBean;
import com.tc.object.ObjectID;
import com.tc.stats.api.RootMBean;

import javax.management.NotCompliantMBeanException;

public class Root extends AbstractTerracottaMBean implements RootMBean {
  private final ObjectID           objectID;
  private final String             rootName;

  public Root(ObjectID rootID, String name) throws NotCompliantMBeanException {
    super(RootMBean.class, false);

    this.objectID = rootID;
    this.rootName = name;
  }

  @Override
  public String getRootName() {
    return this.rootName;
  }

  @Override
  public ObjectID getObjectID() {
    return objectID;
  }

  @Override
  public void reset() {
    /**/
  }
}
