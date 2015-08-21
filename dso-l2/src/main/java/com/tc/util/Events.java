/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

package com.tc.util;

import com.tc.net.NodeID;

/**
 * Static utility methods to access event instances.
 *
 * @author Eugene Shelestovich
 */
public final class Events {

  private Events() {}

  /**
   * Constructs an event what notifies subscribers about new map mutation operation.
   */
  public static WriteOperationCountChangeEvent writeOperationCountIncrementEvent(NodeID source) {
    return writeOperationCountChangeEvent(source, 1);
  }

  /**
   * Constructs an event what notifies subscribers about new map mutation operations.
   */
  public static WriteOperationCountChangeEvent writeOperationCountChangeEvent(NodeID source, int delta) {
    return new WriteOperationCountChangeEvent(source, delta);
  }

  public static final class WriteOperationCountChangeEvent {
    private final NodeID source;
    private final int delta;

    WriteOperationCountChangeEvent(NodeID source, int delta) {
      this.source = source;
      this.delta = delta;
    }

    public int getDelta() {
      return delta;
    }

    public NodeID getSource() {
      return source;
    }
  }
}
