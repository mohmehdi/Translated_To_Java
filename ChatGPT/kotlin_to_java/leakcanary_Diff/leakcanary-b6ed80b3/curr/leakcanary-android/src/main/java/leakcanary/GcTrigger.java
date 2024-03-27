

package leakcanary;

public interface GcTrigger {

  void runGc();

  static final GcTrigger DEFAULT = new GcTrigger() {
    @Override
    public void runGc() {
      Runtime.getRuntime().gc();
      enqueueReferences();
      System.runFinalization();
    }

    private void enqueueReferences() {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new AssertionError();
      }
    }
  };
}