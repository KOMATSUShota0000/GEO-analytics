package com.geo.analytics.domain.phase10;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

public final class ParallelTaskOrchestrator {

  private static final ThreadFactory VIRTUAL_THREADS = Thread.ofVirtual().factory();

  private ParallelTaskOrchestrator() {}

  public static <T> List<T> invokeAllOrdered(List<? extends Callable<T>> tasks) {
    Objects.requireNonNull(tasks);
    final List<StructuredTaskScope.Subtask<T>> subtasks;
    try (var scope =
        StructuredTaskScope.open(
            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
            configuration -> configuration.withThreadFactory(VIRTUAL_THREADS))) {
      subtasks = tasks.stream().map(scope::fork).toList();
      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (StructuredTaskScope.FailedException e) {
      throw failureAsUnchecked(e);
    }
    List<T> out = new ArrayList<>(subtasks.size());
    for (StructuredTaskScope.Subtask<T> subtask : subtasks) {
      out.add(subtask.get());
    }
    return List.copyOf(out);
  }

  private static RuntimeException failureAsUnchecked(StructuredTaskScope.FailedException failed) {
    Throwable cause = failed.getCause();
    if (cause == null) {
      return failed;
    }
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (cause instanceof Error error) {
      throw error;
    }
    return new RuntimeException(cause);
  }
}
