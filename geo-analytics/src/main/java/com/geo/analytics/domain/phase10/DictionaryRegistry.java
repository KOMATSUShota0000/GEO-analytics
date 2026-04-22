package com.geo.analytics.domain.phase10;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class DictionaryRegistry implements AutoCloseable {

  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
  private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

  private DictionaryHandle handle;
  private NativeDatSearcher searcher;

  public DictionaryRegistry(Path initialDictPath) throws IOException {
    OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
    DictionaryHandle loaded = manager.load(initialDictPath);
    NativeDatSearcher built = new NativeDatSearcher(loaded);
    writeLock.lock();
    try {
      handle = loaded;
      searcher = built;
    } finally {
      writeLock.unlock();
    }
  }

  public int search(CharSequence text) {
    readLock.lock();
    try {
      NativeDatSearcher current = searcher;
      if (current == null) {
        throw new IllegalStateException("registry is closed");
      }
      return current.findExact(text);
    } finally {
      readLock.unlock();
    }
  }

  public void reload(Path newDictPath) throws IOException {
    OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
    DictionaryHandle newHandle = manager.load(newDictPath);
    NativeDatSearcher newSearcher = new NativeDatSearcher(newHandle);
    writeLock.lock();
    try {
      DictionaryHandle old = handle;
      handle = newHandle;
      searcher = newSearcher;
      if (old != null) {
        old.close();
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void close() {
    writeLock.lock();
    try {
      DictionaryHandle old = handle;
      handle = null;
      searcher = null;
      if (old != null) {
        old.close();
      }
    } finally {
      writeLock.unlock();
    }
  }
}
