package com.smartcloudsolutions.tollingvision.samples;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.protobuf.ByteString;
import com.smartcloudsolutions.tollingvision.EventRequest;
import com.smartcloudsolutions.tollingvision.EventResponse;
import com.smartcloudsolutions.tollingvision.EventResult;
import com.smartcloudsolutions.tollingvision.PartialResult;
import com.smartcloudsolutions.tollingvision.Status;
import com.smartcloudsolutions.tollingvision.TollingVisionServiceGrpc;
import com.smartcloudsolutions.tollingvision.TollingVisionServiceGrpc.TollingVisionServiceStub;
import com.smartcloudsolutions.tollingvision.samples.model.ImageGroupResult;
import com.smartcloudsolutions.tollingvision.samples.ui.MainScreen;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Stage;

/**
 * TollingVision JavaFX Client - Main application class.
 * Handles JavaFX startup and coordinates between UI and processing logic.
 */
public class AnalysisSampleApp extends Application {

    private ResourceBundle messages;
    private MainScreen mainScreen;
    private volatile boolean stopRequested = false;
    private ManagedChannel currentChannel = null;
    private Task<Void> currentTask = null;

    /**
     * Starts the JavaFX application and initializes the main screen.
     * 
     * @param primaryStage the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) {
        // Load resource bundle
        messages = ResourceBundle.getBundle("messages");

        // Create main screen
        mainScreen = new MainScreen(primaryStage, messages);

        // Set up processing handlers
        mainScreen.setOnStartProcessing(this::startProcessing);
        mainScreen.setOnStopProcessing(this::stopProcessing);

        // Set up application close handler to save configuration
        primaryStage.setOnCloseRequest(event -> {
            mainScreen.saveConfiguration();
        });

        // Show the main screen
        mainScreen.show();
    }

    private void stopProcessing() {
        stopRequested = true;
        log("Stop requested - cancelling processing...");

        // Cancel current task
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }

        // Close gRPC channel
        if (currentChannel != null && !currentChannel.isShutdown()) {
            currentChannel.shutdownNow();
        }

        // Update UI to stopped state
        Platform.runLater(() -> {
            mainScreen.processingProperty().set(false);
            mainScreen.getProgressBar().progressProperty().unbind();
            mainScreen.getProgressBar().setProgress(0);
            mainScreen.getProgressBar().setVisible(false);
            log("Processing stopped by user");
        });
    }

    private void startProcessing() {
        stopRequested = false; // Reset stop flag

        // Save current configuration before starting processing
        mainScreen.saveConfiguration();

        mainScreen.processingProperty().set(true);
        mainScreen.getLogItems().clear();
        resetCounters();
        mainScreen.getProgressBar().setVisible(true);

        File folder = new File(mainScreen.getInputFolder());
        String url = mainScreen.getServiceUrl();
        boolean tls = mainScreen.isTlsEnabled();
        boolean insecure = mainScreen.isInsecureAllowed();
        File csvOut = new File(mainScreen.getCsvOutput());
        int maxPar = mainScreen.getMaxParallel();
        String[] patterns = {
                mainScreen.getGroupPattern(),
                mainScreen.getFrontPattern(),
                mainScreen.getRearPattern(),
                mainScreen.getOverviewPattern()
        };

        ManagedChannel ch0 = null;
        TollingVisionServiceStub stub0 = null;
        try {
            ch0 = tls
                    ? insecure
                            ? NettyChannelBuilder.forTarget(url).useTransportSecurity()
                                    .sslContext(GrpcSslContexts.forClient()
                                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                            .build())
                                    .build()
                            : NettyChannelBuilder.forTarget(url).useTransportSecurity().build()
                    : ManagedChannelBuilder.forTarget(url).usePlaintext().build();
            stub0 = TollingVisionServiceGrpc.newStub(ch0);
        } catch (Exception ex) {
            log(messages.getString("message.error.connection").replace("{0}", ex.getMessage()));
            mainScreen.processingProperty().set(false);
            return;
        }
        final ManagedChannel ch = ch0;
        currentChannel = ch; // Store reference for stopping
        final TollingVisionServiceStub stub = stub0;
        currentTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    validate(folder, csvOut, patterns);
                    Map<String, List<Path>> buckets = bucketize(folder.toPath(), Pattern.compile(patterns[0]));
                    int total = buckets.size();
                    Platform.runLater(() -> mainScreen.groupsDiscoveredProperty().set(total));

                    if (total == 0) {
                        log(messages.getString("message.no.buckets"));
                        return null;
                    }

                    log(messages.getString("message.processing.start").replace("{0}", String.valueOf(total))
                            .replace("{1}", String.valueOf(maxPar)));

                    Semaphore sem = new Semaphore(maxPar);
                    CountDownLatch done = new CountDownLatch(total);
                    AtomicInteger cnt = new AtomicInteger();

                    for (var e : buckets.entrySet()) {
                        if (stopRequested) {
                            log("Processing stopped - remaining tasks cancelled");
                            break;
                        }

                        String bucket = e.getKey();
                        List<Path> imgs = e.getValue();
                        new Thread(() -> {
                            try {
                                if (stopRequested) {
                                    done.countDown();
                                    sem.release();
                                    return;
                                }

                                sem.acquire();

                                if (stopRequested) {
                                    done.countDown();
                                    sem.release();
                                    return;
                                }

                                EventRequest eventRequest = buildReq(imgs, patterns[1], patterns[2], patterns[3]);
                                if (eventRequest == null) {
                                    log("Skip " + bucket);
                                    done.countDown();
                                    sem.release();
                                    return;
                                }

                                Platform.runLater(() -> mainScreen.requestsSentProperty()
                                        .set(mainScreen.requestsSentProperty().get() + 1));

                                stub.analyze(eventRequest, new StreamObserver<>() {
                                    EventResult eventResult = null;
                                    List<PartialResult> partialResults = new ArrayList<>();

                                    @Override
                                    public void onNext(EventResponse r) {
                                        if (r.hasEventResult()) {
                                            eventResult = r.getEventResult();
                                        } else if (r.hasPartialResult()) {
                                            PartialResult partial = r.getPartialResult();
                                            if (partial.hasResult()
                                                    && partial.getResult().getStatus() == Status.RESULT) {
                                                partialResults.add(partial.toBuilder().build());
                                            }
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        if (!stopRequested) {
                                            log("ERR " + bucket + ": " + t.getMessage());
                                            Platform.runLater(() -> mainScreen.responsesErrorProperty()
                                                    .set(mainScreen.responsesErrorProperty().get() + 1));
                                        }
                                        done.countDown();
                                        sem.release();
                                    }

                                    @Override
                                    public void onCompleted() {
                                        if (eventResult != null && !stopRequested) {
                                            ImageGroupResult result = new ImageGroupResult(bucket, eventResult, imgs,
                                                    patterns[1], patterns[2], patterns[3]);

                                            // Create SearchResponse data from EventResult for each image
                                            // This extracts the analysis data and associates it with individual images
                                            populateSearchResponseData(result,
                                                    eventRequest, eventResult, partialResults, imgs);

                                            Platform.runLater(() -> {
                                                mainScreen.getLogItems().add(result);
                                                mainScreen.responsesOkProperty()
                                                        .set(mainScreen.responsesOkProperty().get() + 1);
                                            });
                                            updateProgress(cnt.incrementAndGet(), total);
                                        }
                                        done.countDown();
                                        sem.release();
                                    }
                                });
                            } catch (InterruptedException | IOException ignored) {
                                done.countDown();
                                sem.release();
                            }
                        }, "th-" + bucket).start();
                    }

                    done.await();

                    if (!stopRequested) {
                        writeCsv(csvOut.toPath(), new ArrayList<>(mainScreen.getLogItems()));
                        log(messages.getString("message.csv.saved").replace("{0}", csvOut.toString()));
                    } else {
                        log("Processing was stopped - CSV not saved");
                    }
                } catch (Exception ex) {
                    log("ERROR: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                Platform.runLater(() -> {
                    mainScreen.processingProperty().set(false);
                    mainScreen.getProgressBar().progressProperty().unbind();
                    mainScreen.getProgressBar().setProgress(0);
                    mainScreen.getProgressBar().setVisible(false);
                });
                ch.shutdownNow();
            }
        };
        mainScreen.getProgressBar().progressProperty().bind(currentTask.progressProperty());
        new Thread(currentTask).start();
    }

    private void resetCounters() {
        mainScreen.groupsDiscoveredProperty().set(0);
        mainScreen.requestsSentProperty().set(0);
        mainScreen.responsesOkProperty().set(0);
        mainScreen.responsesErrorProperty().set(0);
    }

    // Helper methods for processing
    private Map<String, List<Path>> bucketize(Path root, Pattern pattern) throws IOException {
        Map<String, List<Path>> map = new HashMap<>();
        Files.walk(root).filter(Files::isRegularFile).forEach(f -> {
            Matcher m = pattern.matcher(f.getFileName().toString());
            if (m.find())
                map.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(f);
        });
        return map;
    }

    private EventRequest buildReq(List<Path> files, String frontPattern, String rearPattern, String overviewPattern)
            throws IOException {
        Pattern fp = Pattern.compile(frontPattern);
        Pattern rp = Pattern.compile(rearPattern);
        Pattern op = Pattern.compile(overviewPattern);
        EventRequest.Builder b = EventRequest.newBuilder();
        int cnt = 0;
        for (Path p : files) {
            byte[] d;
            try {
                d = Files.readAllBytes(p);
            } catch (IOException e) {
                continue;
            }
            com.smartcloudsolutions.tollingvision.Image img = com.smartcloudsolutions.tollingvision.Image.newBuilder()
                    .setName(p.getFileName().toString())
                    .setData(ByteString.copyFrom(d))
                    .build();
            String n = p.getFileName().toString();
            if (op.matcher(n).find()) {
                b.addOverviewImage(img);
                cnt++;
            } else if (fp.matcher(n).find()) {
                b.addFrontImage(img);
                cnt++;
            } else if (rp.matcher(n).find()) {
                b.addRearImage(img);
                cnt++;
            }
        }
        return cnt == 0 ? null : b.build();
    }

    private void writeCsv(Path out, List<ImageGroupResult> results) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write(
                    "Bucket,Front Images,Rear Images,Overview Images,Front Plate,Front Plate Alt,Rear Plate,Rear Plate Alt,MMR,MMR Alt\n");
            for (ImageGroupResult result : results) {
                // Exclude any lines starting with [LOG] from CSV output
                if (!result.getBucket().startsWith("[LOG]")) {
                    bw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            result.getBucket(), result.getFrontNames(), result.getRearNames(), result.getOverNames(),
                            result.getFrontPlate(), result.getFrontAlt(), result.getRearPlate(), result.getRearAlt(),
                            result.getMmr(), result.getMmrAlt()));
                }
            }
        }
    }

    private void validate(File dir, File csv, String[] patterns) {
        if (!dir.isDirectory())
            throw new IllegalArgumentException(messages.getString("message.error.folder.invalid"));
        if (csv.getParentFile() != null && !csv.getParentFile().exists())
            throw new IllegalArgumentException(messages.getString("message.error.csv.directory"));
        for (int i = 0; i < patterns.length; i++)
            if (patterns[i].isBlank())
                throw new IllegalArgumentException(
                        messages.getString("message.error.regex.empty").replace("{0}", String.valueOf(i + 1)));
    }

    private void log(String msg) {
        Platform.runLater(() -> mainScreen.getLogItems().add(new ImageGroupResult("[LOG]", msg)));
    }

    private void populateSearchResponseData(ImageGroupResult result, EventRequest eventRequest, EventResult eventResult,
            List<PartialResult> partialResults, List<Path> images) {
        // Create SearchResponse objects from EventResult data
        // Only create vehicle data if we have actual detection results - no hardcoded
        // boxes

        for (Path imagePath : images) {
            eventRequest.getOverviewImageList().stream()
                    .filter(img -> img.getName().equals(imagePath.getFileName().toString()))
                    .findFirst().ifPresent(img -> {
                        int idx = eventRequest.getOverviewImageList().indexOf(img);
                        PartialResult pr = partialResults.stream()
                                .filter(pr0 -> pr0.hasResult() && pr0
                                        .getResultType() == com.smartcloudsolutions.tollingvision.ResultType.OVERVIEW
                                        && pr0.getResultIndex() == idx)
                                .findFirst()
                                .orElse(null);
                        if (pr != null) {
                            result.addImageAnalysis(imagePath, pr.getResult());
                        }
                    });

            eventRequest.getFrontImageList().stream()
                    .filter(img -> img.getName().equals(imagePath.getFileName().toString()))
                    .findFirst().ifPresent(img -> {
                        int idx = eventRequest.getFrontImageList().indexOf(img);
                        PartialResult pr = partialResults.stream()
                                .filter(pr0 -> pr0.hasResult() && pr0
                                        .getResultType() == com.smartcloudsolutions.tollingvision.ResultType.FRONT
                                        && pr0.getResultIndex() == idx)
                                .findFirst()
                                .orElse(null);
                        if (pr != null) {
                            result.addImageAnalysis(imagePath, pr.getResult());
                        }
                    });

            eventRequest.getRearImageList().stream()
                    .filter(img -> img.getName().equals(imagePath.getFileName().toString()))
                    .findFirst().ifPresent(img -> {
                        int idx = eventRequest.getRearImageList().indexOf(img);
                        PartialResult pr = partialResults.stream()
                                .filter(pr0 -> pr0.hasResult() && pr0
                                        .getResultType() == com.smartcloudsolutions.tollingvision.ResultType.REAR
                                        && pr0.getResultIndex() == idx)
                                .findFirst()
                                .orElse(null);
                        if (pr != null) {
                            result.addImageAnalysis(imagePath, pr.getResult());
                        }
                    });

        }
    }

    /**
     * Main entry point for the application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}