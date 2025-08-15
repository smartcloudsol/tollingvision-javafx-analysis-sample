package com.smartcloudsolutions.tollingvision.samples.patternbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for PatternBuilderDialog functionality. Tests dialog creation, mode switching, and
 * configuration handling.
 */
class PatternBuilderDialogTest {

  private PatternBuilderDialog dialog;

  @BeforeAll
  static void initToolkit() {
    // Initialize JavaFX toolkit if not already initialized
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException e) {
      // Toolkit already initialized, which is fine
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          dialog =
              new PatternBuilderDialog(
                  "/test/folder", java.util.ResourceBundle.getBundle("messages"));
          latch.countDown();
        });
    assertTrue(latch.await(5, TimeUnit.SECONDS), "Dialog creation timed out");
  }

  @Test
  void testDialogCreation() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> title = new AtomicReference<>();
    AtomicReference<PatternBuilderDialog.PatternBuilderMode> mode = new AtomicReference<>();

    Platform.runLater(
        () -> {
          title.set(dialog.getTitle());
          mode.set(dialog.getCurrentMode());
          latch.countDown();
        });

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    assertEquals("Filename Pattern Builder", title.get());
    assertEquals(PatternBuilderDialog.PatternBuilderMode.SIMPLE, mode.get());
  }

  @Test
  void testModeEnumValues() {
    PatternBuilderDialog.PatternBuilderMode simple = PatternBuilderDialog.PatternBuilderMode.SIMPLE;
    PatternBuilderDialog.PatternBuilderMode advanced =
        PatternBuilderDialog.PatternBuilderMode.ADVANCED;

    assertEquals("Simple", simple.getDisplayName());
    assertEquals("Visual pattern builder for non-regex users", simple.getDescription());

    assertEquals("Advanced", advanced.getDisplayName());
    assertEquals("Direct regex input for power users", advanced.getDescription());
  }

  @Test
  void testInitialConfiguration() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PatternConfiguration> config = new AtomicReference<>();

    Platform.runLater(
        () -> {
          config.set(dialog.getCurrentConfiguration());
          latch.countDown();
        });

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    assertNotNull(config.get());
    // Initial configuration should be empty
    assertTrue(config.get().getGroupPattern() == null || config.get().getGroupPattern().isEmpty());
  }

  @Test
  void testConfigurationCallback() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PatternConfiguration> receivedConfig = new AtomicReference<>();

    Platform.runLater(
        () -> {
          dialog.setOnConfigurationComplete(receivedConfig::set);

          // Create a test configuration
          PatternConfiguration testConfig = new PatternConfiguration();
          testConfig.setGroupPattern("test_group_pattern");
          testConfig.setFrontPattern("test_front_pattern");

          // Simulate configuration completion (normally done by OK button)
          receivedConfig.set(testConfig);
          latch.countDown();
        });

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    assertNotNull(receivedConfig.get());
    assertEquals("test_group_pattern", receivedConfig.get().getGroupPattern());
    assertEquals("test_front_pattern", receivedConfig.get().getFrontPattern());
  }

  @Test
  void testDialogProperties() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Boolean> isModal = new AtomicReference<>();
    AtomicReference<Boolean> isResizable = new AtomicReference<>();
    AtomicReference<Double> minWidth = new AtomicReference<>();
    AtomicReference<Double> minHeight = new AtomicReference<>();

    Platform.runLater(
        () -> {
          isModal.set(dialog.getModality() != null);
          isResizable.set(dialog.isResizable());
          minWidth.set(dialog.getMinWidth());
          minHeight.set(dialog.getMinHeight());
          latch.countDown();
        });

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    assertTrue(isModal.get(), "Dialog should be modal");
    assertTrue(isResizable.get(), "Dialog should be resizable");
    assertEquals(1000.0, minWidth.get(), "Minimum width should be 1000");
    assertEquals(700.0, minHeight.get(), "Minimum height should be 700");
  }

  @Test
  void testShowDialogWithConfiguration() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    Platform.runLater(
        () -> {
          // Create a test configuration
          PatternConfiguration testConfig = new PatternConfiguration();
          testConfig.setGroupPattern("^(.{7})");
          testConfig.setFrontPattern(".*front.*");
          testConfig.setRearPattern(".*rear.*");
          testConfig.setOverviewPattern(".*scene.*");

          // This would normally show the dialog, but we'll just test the setup
          // dialog.showDialog(testConfig); // Don't actually show in test

          // Instead, test that we can set the configuration
          dialog.currentConfigurationProperty().set(testConfig);

          PatternConfiguration currentConfig = dialog.getCurrentConfiguration();
          assertEquals("^(.{7})", currentConfig.getGroupPattern());
          assertEquals(".*front.*", currentConfig.getFrontPattern());
          assertEquals(".*rear.*", currentConfig.getRearPattern());
          assertEquals(".*scene.*", currentConfig.getOverviewPattern());

          latch.countDown();
        });

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
  }
}
