package com.tc.objectserver.persistence;


import com.tc.text.PrettyPrintable;
import com.tc.text.PrettyPrinter;

import java.io.IOException;

import org.terracotta.persistence.IPersistentStorage;
/**
 * @author tim
 */
public class Persistor implements PrettyPrintable {
  private final IPersistentStorage persistentStorage;

  private volatile boolean started = false;

  private final ClusterStatePersistor clusterStatePersistor;

  private ClientStatePersistor clientStatePersistor;
  private SequenceManager sequenceManager;
  private final EntityPersistor entityPersistor;
  private final TransactionOrderPersistor transactionOrderPersistor;

  public Persistor(IPersistentStorage persistentStorage) {
    // The persistor only wants to operate on opened storage.
    try {
      persistentStorage.open();
    } catch (IOException e) {
      // Fall back to creating a new one since this probably means it doesn't exist and the Persitor has no notion of which
      // mode (open/create) it should prefer.
      try {
        persistentStorage.create();
      } catch (IOException e1) {
        // We are not expecting both to fail.
        throw new RuntimeException(e1);
      }
    }
    this.persistentStorage = persistentStorage;
    this.clusterStatePersistor = new ClusterStatePersistor(persistentStorage);
    this.entityPersistor = new EntityPersistor(persistentStorage);
    this.transactionOrderPersistor = new TransactionOrderPersistor(persistentStorage);
  }

  public void start() {
    sequenceManager = new SequenceManager(persistentStorage);
    clientStatePersistor = new ClientStatePersistor(sequenceManager, persistentStorage);

    started = true;
  }

  public void close() {
    persistentStorage.close();
  }
  
  public ClientStatePersistor getClientStatePersistor() {
    checkStarted();
    return clientStatePersistor;
  }

  public ClusterStatePersistor getClusterStatePersistor() {
    return clusterStatePersistor;
  }

  public SequenceManager getSequenceManager() {
    checkStarted();
    return sequenceManager;
  }
  
  public EntityPersistor getEntityPersistor() {
    return this.entityPersistor;
  }

  public TransactionOrderPersistor getTransactionOrderPersistor() {
    return this.transactionOrderPersistor;
  }

  protected final void checkStarted() {
    if (!started) {
      throw new IllegalStateException("Persistor is not yet started.");
    }
  }

  @Override
  public PrettyPrinter prettyPrint(PrettyPrinter out) {
    out.print(getClass().getName()).flush();
    if (!started) {
      out.indent().print("PersistorImpl not started.").flush();
    } else {
      out.println(persistentStorage);
    }
    return out;
  }
}
