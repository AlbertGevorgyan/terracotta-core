/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package org.terracotta.ui.session;

import com.tc.admin.common.XRootNode;
import com.tc.admin.common.XTreeModel;

import javax.swing.tree.DefaultMutableTreeNode;

public class WebAppTreeModel extends XTreeModel {
  private SessionIntegratorFrame frame;

  public WebAppTreeModel(SessionIntegratorFrame frame, WebApp[] webApps) {
    super(new XRootNode("WebApps"));

    this.frame = frame;

    DefaultMutableTreeNode WebAppsNode = (DefaultMutableTreeNode) getRoot();
    if (webApps != null) {
      for (int i = 0; i < webApps.length; i++) {
        insertNodeInto(new WebAppNode(webApps[i]), WebAppsNode, i);
      }
    }
  }

  public void remove(String name) {
    XRootNode webAppsNode = (XRootNode) getRoot();
    int childCount = webAppsNode.getChildCount();
    WebAppNode webAppNode;

    for (int i = 0; i < childCount; i++) {
      webAppNode = (WebAppNode) webAppsNode.getChildAt(i);
      if (webAppNode.getName().equals(name)) {
        removeNodeFromParent(webAppNode);
        return;
      }
    }
  }

  public WebAppNode add(WebApp webApp) {
    remove(webApp.getName());

    DefaultMutableTreeNode webAppsNode = (DefaultMutableTreeNode) getRoot();
    int childCount = webAppsNode.getChildCount();
    WebAppNode webAppNode = new WebAppNode(webApp);

    insertNodeInto(webAppNode, webAppsNode, childCount);

    return webAppNode;
  }

  public void setRefreshEnabled(boolean enabled) {
    XRootNode webAppsNode = (XRootNode) getRoot();
    int childCount = webAppsNode.getChildCount();
    WebAppNode webAppNode;

    for (int i = 0; i < childCount; i++) {
      webAppNode = (WebAppNode) webAppsNode.getChildAt(i);
      webAppNode.setRefreshEnabled(enabled);
    }
  }

  public void refresh(WebApp webApp) {
    frame.refresh(webApp);
  }

  public void setRemoveEnabled(boolean enabled) {
    XRootNode webAppsNode = (XRootNode) getRoot();
    int childCount = webAppsNode.getChildCount();
    WebAppNode webAppNode;

    for (int i = 0; i < childCount; i++) {
      webAppNode = (WebAppNode) webAppsNode.getChildAt(i);
      webAppNode.setRemoveEnabled(enabled);
    }
  }

  public void remove(WebApp webApp) {
    frame.remove(webApp);
  }

  public void updateLinks(boolean tomcat1Ready, boolean tomcat2Ready) {
    XRootNode webAppsNode = (XRootNode) getRoot();
    int childCount = webAppsNode.getChildCount();
    WebAppNode webAppNode;

    for (int i = 0; i < childCount; i++) {
      webAppNode = (WebAppNode) webAppsNode.getChildAt(i);
      webAppNode.updateLinks(tomcat1Ready, tomcat2Ready);
    }
  }
}
