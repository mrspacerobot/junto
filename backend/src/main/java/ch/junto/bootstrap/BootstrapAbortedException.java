package ch.junto.bootstrap;

/**
 * A refusal the operator is expected to read and act on, reported as a single message and a
 * non-zero exit status rather than as a failure of the application itself.
 */
class BootstrapAbortedException extends RuntimeException {

  BootstrapAbortedException(String message) {
    super(message);
  }
}
