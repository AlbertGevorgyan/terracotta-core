/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.objectserver.impl;

import com.tc.object.ObjectID;
import com.tc.objectserver.api.EvictableMap;
import com.tc.objectserver.api.EvictionTrigger;
import com.tc.objectserver.context.ServerMapEvictionContext;

import java.util.Map;

/**
 *
 * @author mscott
 */
public abstract class AbstractEvictionTrigger implements EvictionTrigger {
    
    private final ObjectID oid;
    private boolean started = false;
    private boolean  evicting = false;
    private boolean  mapEvicting = false;
    boolean processed = false;
    private String name;
    private boolean pinned;
    private long startTime = 0;
    private long endTime = 0;
    private int count;

    public AbstractEvictionTrigger(ObjectID oid) {
        this.oid = oid;
    }

    @Override
    public ObjectID getId() {
        return oid;
    }
     
    @Override
    public String getName() {
        return getClass().getName();
    }
    
    public int boundsCheckSampleSize(int sampled) {
        if ( sampled < 0 ) {
            sampled = 0;
        }
        if ( sampled > 100000 ) {
            sampled = 100000;
        }
        return sampled;
    }
    
    @Override
    public boolean startEviction(EvictableMap map) {
        started = true;
        name = map.getCacheName();
        pinned = (map.getMaxTotalCount() == 0);
        startTime = System.currentTimeMillis();
        mapEvicting = map.isEvicting();
        if ( !pinned && !map.isEvicting() && map.getSize() > 0 ) {
            return map.startEviction();
        } else {
            return false;
        }
    }
    
    protected boolean isPinned() {
        return pinned;
    }
    
    @Override
    public void completeEviction(EvictableMap map) {
        if ( !started ) {
            throw new AssertionError("sample not started");
        }
        if ( !processed ) {
            throw new AssertionError("sample not processed");
        }
        endTime = System.currentTimeMillis() + 1;
        if ( !evicting ) {
            map.evictionCompleted();
        }
        
    }
    
    private Map<Object, ObjectID> processSample(Map<Object, ObjectID> sample) {
        evicting = !sample.isEmpty();
        count = sample.size();
        processed = true;
        return sample;
    }
    
    protected ServerMapEvictionContext createEvictionContext(String className, Map<Object, ObjectID> sample) {
        sample = processSample(sample);
        if ( sample.isEmpty() ) {
            return null;
        }
        return new ServerMapEvictionContext(this, sample, className, name);
        
    }
        
    @Override
    public long getRuntimeInMillis() {
        if ( startTime == 0 || endTime == 0 ) {
            return 0;
        }
        return ( endTime - startTime );
    }
    
    @Override
    public int getCount() {
        return count;
    }
    
    @Override
    public boolean isValid() {
        return !started;
    }

    @Override
    public String toString() {
        return "AbstractEvictionTrigger{"
                + "name=" + name + " - " + getId() + (( pinned ) ? " - PINNED" : "")
                + ", count=" + count
                + ", started=" + started
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", processed=" + processed
                + ", map evicting=" + mapEvicting
                + ", evicting=" + evicting + '}';
    }
}
