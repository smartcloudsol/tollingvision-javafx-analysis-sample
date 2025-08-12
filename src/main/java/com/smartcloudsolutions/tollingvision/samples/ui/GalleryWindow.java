package com.smartcloudsolutions.tollingvision.samples.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

import com.google.protobuf.util.JsonFormat;
import com.smartcloudsolutions.tollingvision.SearchResponse;
import com.smartcloudsolutions.tollingvision.samples.model.ImageGroupResult;
import com.smartcloudsolutions.tollingvision.samples.util.OverlayUtils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Gallery window for displaying image analysis results.
 * Shows individual images with overlays, plate thumbnails, and analysis data.
 */
public class GalleryWindow {

    private final ResourceBundle messages;

    private final Stage galleryStage;
    private final ImageGroupResult result;
    private final List<Path> images;

    private int currentImageIndex = 0;
    private ImageView mainImageView;
    private javafx.scene.canvas.Canvas overlayCanvas;
    private VBox currentImageDataSection;

    private Button prevButton;
    private Button nextButton;
    private Label pageLabel;
    private StackPane fixedImageContainer;
    private double zoomFactor = 1.0;
    private Label zoomLabel;
    private Button resetZoomButton;

    /**
     * Creates a new gallery window for the given image group result.
     * 
     * @param result   the image group result containing analysis data
     * @param messages resource bundle for internationalization
     */
    public GalleryWindow(ImageGroupResult result, ResourceBundle messages) {
        this.result = result;
        this.messages = messages;

        this.images = result.getAllImagePaths();

        this.galleryStage = new Stage(StageStyle.DECORATED);
        this.galleryStage.setTitle(messages.getString("gallery.title") + " - " + result.getBucket());
        this.galleryStage.initModality(Modality.APPLICATION_MODAL);

        initializeUI();
        setupKeyboardHandling();
    }

    /**
     * Initializes the user interface components.
     */
    private void initializeUI() {
        if (images.isEmpty()) {
            return;
        }

        // BorderPane layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("gallery-root");

        // Enhanced summary section with 50/50 two-column layout
        HBox summary = createEnhancedSummarySection();
        root.setTop(summary);

        // Thumbnail strip (left)
        VBox thumbnailSection = createThumbnailSection();
        root.setLeft(thumbnailSection);

        // Current image data section (right)
        currentImageDataSection = createCurrentImageDataSection();
        root.setRight(currentImageDataSection);

        // Image pagination with individual overlays (center)
        VBox imageSection = createImagePaginationSection();
        root.setCenter(imageSection);

        // Create scene
        Scene scene = new Scene(root, 1400, 900);
        galleryStage.setScene(scene);
    }

    /**
     * Sets up keyboard navigation for the gallery.
     */
    private void setupKeyboardHandling() {
        galleryStage.getScene().setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case LEFT:
                case UP:
                    navigateImage(-1);
                    event.consume();
                    break;
                case RIGHT:
                case DOWN:
                    navigateImage(1);
                    event.consume();
                    break;
                case ESCAPE:
                    galleryStage.close();
                    event.consume();
                    break;
                case ENTER:
                    if (event.isControlDown()) {
                        resetZoom();
                        event.consume();
                    }
                    break;
            }
        });
    }

    /**
     * Creates the enhanced summary section with 50/50 two-column layout.
     * 
     * @return HBox containing the summary section
     */
    private HBox createEnhancedSummarySection() {
        HBox summarySection = new HBox(20);
        summarySection.setPadding(new Insets(20));
        summarySection.setAlignment(Pos.CENTER_LEFT);
        summarySection.getStyleClass().add("gallery-summary");

        // ANPR/MMR Column (50% width)
        VBox anprMmrColumn = createAnprMmrColumn(result);
        HBox.setHgrow(anprMmrColumn, Priority.ALWAYS);
        anprMmrColumn.setMaxWidth(Double.MAX_VALUE);

        // Analysis Data Column (50% width)
        VBox analysisColumn = createAnalysisDataColumn();
        HBox.setHgrow(analysisColumn, Priority.ALWAYS);
        analysisColumn.setMaxWidth(Double.MAX_VALUE);

        summarySection.getChildren().addAll(anprMmrColumn, analysisColumn);
        return summarySection;
    }

    /**
     * Creates the ANPR and MMR summary column.
     * 
     * @param result the image group result
     * @return VBox containing ANPR and MMR data
     */
    private VBox createAnprMmrColumn(ImageGroupResult result) {
        VBox column = new VBox(20);
        column.getStyleClass().add("gallery-sidebar");
        column.setPadding(new Insets(15));
        column.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px;");
        // Make the column fill available space
        VBox.setVgrow(column, Priority.ALWAYS);

        Label titleLabel = new Label("Event Summary");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // ANPR Summary - compact single line format
        if (!result.getFrontPlate().isEmpty() || !result.getRearPlate().isEmpty()) {
            VBox anprSection = new VBox(5);
            Label anprTitle = new Label(messages.getString("gallery.anpr.results"));
            anprTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            anprSection.getChildren().add(anprTitle);

            if (!result.getFrontPlate().isEmpty()) {
                String frontPlateInfo = result.getFrontPlate();
                String plateText = extractPlateText(frontPlateInfo);
                String jurisdiction = extractJurisdiction(frontPlateInfo);
                String category = extractCategory(frontPlateInfo);
                String confidence = extractConfidence(frontPlateInfo);

                // Compact single line format
                StringBuilder frontLine = new StringBuilder("Front: " + plateText);
                if (!jurisdiction.isEmpty())
                    frontLine.append(" | ").append(jurisdiction);
                if (!category.isEmpty())
                    frontLine.append(" | ").append(category);
                if (!confidence.isEmpty())
                    frontLine.append(" | ").append(confidence);

                anprSection.getChildren().add(new Label(frontLine.toString()));
            }

            if (!result.getRearPlate().isEmpty()) {
                String rearPlateInfo = result.getRearPlate();
                String plateText = extractPlateText(rearPlateInfo);
                String jurisdiction = extractJurisdiction(rearPlateInfo);
                String category = extractCategory(rearPlateInfo);
                String confidence = extractConfidence(rearPlateInfo);

                // Compact single line format
                StringBuilder rearLine = new StringBuilder("Rear: " + plateText);
                if (!jurisdiction.isEmpty())
                    rearLine.append(" | ").append(jurisdiction);
                if (!category.isEmpty())
                    rearLine.append(" | ").append(category);
                if (!confidence.isEmpty())
                    rearLine.append(" | ").append(confidence);

                anprSection.getChildren().add(new Label(rearLine.toString()));
            }
            column.getChildren().add(anprSection);
        }

        // MMR Summary - compact format without viewpoint
        if (!result.getMmr().isEmpty()) {
            VBox mmrSection = new VBox(5);
            Label mmrTitle = new Label(messages.getString("gallery.mmr.results"));
            mmrTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            mmrSection.getChildren().add(mmrTitle);

            // Format: <make> <model> <variation> <generation> | <category> (<body type>)
            if (result.getEventResult() != null && result.getEventResult().hasMmr()) {
                var mmr = result.getEventResult().getMmr();
                StringBuilder mmrLine = new StringBuilder();

                // Build the main part: <make> <model> <variation> <generation>
                if (!mmr.getMake().isEmpty()) {
                    mmrLine.append(mmr.getMake());
                }
                if (!mmr.getModel().isEmpty()) {
                    if (mmrLine.length() > 0)
                        mmrLine.append(" ");
                    mmrLine.append(mmr.getModel());
                }
                // Note: variation field might not exist in proto, using generation for now
                if (!mmr.getGeneration().isEmpty()) {
                    if (mmrLine.length() > 0)
                        mmrLine.append(" ");
                    mmrLine.append(mmr.getGeneration());
                }

                // Add the separator and category/body type part
                boolean hasCategory = !mmr.getCategory().isEmpty();
                boolean hasBodyType = !mmr.getBodyType().isEmpty();

                if (hasCategory || hasBodyType) {
                    mmrLine.append(" | ");
                    if (hasCategory) {
                        mmrLine.append(mmr.getCategory());
                    }
                    if (hasBodyType) {
                        mmrLine.append(" (").append(mmr.getBodyType()).append(")");
                    }
                }

                if (mmrLine.length() > 0) {
                    mmrSection.getChildren().add(new Label(mmrLine.toString()));
                }
            } else {
                // Fallback to parsing string format
                String mmrData = result.getMmr();
                String[] mmrParts = mmrData.split(" \\(");
                if (mmrParts.length > 0) {
                    mmrSection.getChildren().add(new Label("Make/Model: " + mmrParts[0]));
                }
            }
            column.getChildren().add(mmrSection);
        }

        column.getChildren().add(0, titleLabel);
        return column;
    }

    /**
     * Creates the analysis data column with JSON output.
     * 
     * @return VBox containing analysis data
     */
    private VBox createAnalysisDataColumn() {
        VBox column = new VBox(10);
        column.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 15px; -fx-background-radius: 8px;");

        Label title = new Label(messages.getString("gallery.analysis.data"));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        TextArea jsonTextArea = new TextArea();
        jsonTextArea.getStyleClass().add("json-area");
        jsonTextArea.setEditable(false);
        jsonTextArea.setWrapText(false);
        jsonTextArea.setPrefRowCount(12);
        jsonTextArea.setMaxHeight(200);

        try {
            // Use Protobuf JsonFormat for EventResult - no camelCase enforcement
            if (result.getEventResult() != null) {
                String jsonString = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .print(result.getEventResult());
                jsonTextArea.setText(jsonString);
            } else {
                jsonTextArea.setText("No analysis data available for this event.");
            }
        } catch (Exception e) {
            jsonTextArea.setText("Error generating JSON: " + e.getMessage());
        }

        column.getChildren().addAll(title, jsonTextArea);
        return column;
    }

    /**
     * Creates the thumbnail section with clickable image thumbnails.
     * 
     * @return VBox containing the thumbnail strip
     */
    private VBox createThumbnailSection() {
        VBox thumbnailSection = new VBox(10);
        thumbnailSection.setPadding(new Insets(15, 10, 15, 15)); // Reduce right padding for symmetrical margins
        thumbnailSection.setPrefWidth(220); // Slightly wider to accommodate 6 thumbnails
        thumbnailSection.getStyleClass().add("gallery-sidebar");

        Label title = new Label("Image Gallery");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        FlowPane thumbnailContainer = new FlowPane();
        thumbnailContainer.setHgap(6);
        thumbnailContainer.setVgap(6);
        thumbnailContainer.setPadding(new Insets(5));
        thumbnailContainer.setPrefWrapLength(220); // Wrap at container width
        thumbnailContainer.setAlignment(Pos.CENTER_LEFT); // Align thumbnails to left for consistent layout

        // Create thumbnails for all images
        for (int i = 0; i < images.size(); i++) {
            final int imageIndex = i;
            Path imagePath = images.get(i);

            VBox thumbnailBox = new VBox(5);
            thumbnailBox.setAlignment(Pos.CENTER);

            ImageView thumbnail = new ImageView();
            thumbnail.setFitWidth(100);
            thumbnail.setFitHeight(75);
            thumbnail.setPreserveRatio(true);
            thumbnail.setSmooth(true);

            try {
                Image image = new Image(imagePath.toUri().toString());
                thumbnail.setImage(image);
            } catch (Exception e) {
                // Handle image loading error
                System.err.println("Error loading thumbnail: " + e.getMessage());
            }

            // Add click handler to navigate to this image
            thumbnail.setOnMouseClicked(event -> {
                currentImageIndex = imageIndex;
                loadCurrentImage();
                updateNavigationButtons();
                updateThumbnailSelection();
                resetZoom();
            });

            // Add selection styling
            if (i == currentImageIndex) {
                thumbnail.setStyle("-fx-effect: dropshadow(gaussian, #0066cc, 3, 0.5, 0, 0);");
            } else {
                thumbnail.setStyle("");
            }

            Label imageLabel = new Label(imagePath.getFileName().toString());
            imageLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666; -fx-text-alignment: center;");
            imageLabel.setWrapText(true);
            imageLabel.setMaxWidth(100);

            thumbnailBox.getChildren().addAll(thumbnail, imageLabel);
            thumbnailContainer.getChildren().add(thumbnailBox);
        }

        scrollPane.setContent(thumbnailContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        thumbnailSection.getChildren().addAll(title, scrollPane);
        return thumbnailSection;
    }

    /**
     * Creates the image pagination section with zoom and pan controls.
     * 
     * @return VBox containing the image display and controls
     */
    private VBox createImagePaginationSection() {
        VBox imageSection = new VBox(15);
        imageSection.setPadding(new Insets(15));
        imageSection.setAlignment(Pos.CENTER);

        // Add "Current Image" header
        Label currentImageHeader = new Label("Current Image");
        currentImageHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #333;");
        currentImageHeader.setAlignment(Pos.CENTER);

        // Dynamic image container - height adjusts to fit window with controls
        fixedImageContainer = new StackPane();
        fixedImageContainer.setPrefSize(700, 400); // Reduced default height for controls
        fixedImageContainer.setMaxSize(700, Region.USE_COMPUTED_SIZE);
        fixedImageContainer.setMinSize(700, 300); // Minimum usable height
        fixedImageContainer.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");

        // Make the image container grow to fill available space
        VBox.setVgrow(fixedImageContainer, Priority.ALWAYS);

        // Clipping will be updated dynamically
        updateImageContainerClip();

        // Main image view - this will be zoomed and panned
        mainImageView = new ImageView();
        mainImageView.setPreserveRatio(true);
        mainImageView.setFitHeight(400);
        mainImageView.setFitWidth(700);

        // Overlay canvas for individual bounding boxes - matches image view
        overlayCanvas = new javafx.scene.canvas.Canvas(700, 400);

        fixedImageContainer.getChildren().addAll(mainImageView, overlayCanvas);

        // Add zoom and pan functionality to the image and overlay
        setupZoomAndPan();

        // Zoom controls
        HBox zoomControls = new HBox(10);
        zoomControls.setAlignment(Pos.CENTER);

        zoomLabel = new Label("100%");
        zoomLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        resetZoomButton = new Button("Reset Zoom & Pan");
        resetZoomButton.setOnAction(e -> resetZoom());

        zoomControls.getChildren().addAll(new Label("Zoom:"), zoomLabel, resetZoomButton);

        // Navigation controls with pagination buttons
        HBox navControls = new HBox(15);
        navControls.setAlignment(Pos.CENTER);

        prevButton = new Button("◀ Previous");
        prevButton.getStyleClass().add("nav-button");
        prevButton.setOnAction(e -> navigateImage(-1));

        pageLabel = new Label("1 / " + images.size());
        pageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        nextButton = new Button("Next ▶");
        nextButton.getStyleClass().add("nav-button");
        nextButton.setOnAction(e -> navigateImage(1));

        navControls.getChildren().addAll(prevButton, pageLabel, nextButton);

        // Load initial image and ensure index 0 is preselected with immediate data
        // rendering
        currentImageIndex = 0;
        loadCurrentImage();
        updateNavigationButtons();
        updateThumbnailSelection();

        // Listen for container size changes to update clipping and canvas
        fixedImageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            updateImageContainerClip();
            updateCanvasSize();
        });

        imageSection.getChildren().addAll(currentImageHeader, fixedImageContainer, zoomControls, navControls);
        return imageSection;
    }

    /**
     * Updates the clipping rectangle for the image container.
     */
    private void updateImageContainerClip() {
        double width = fixedImageContainer.getPrefWidth();
        double height = fixedImageContainer.getHeight() > 0 ? fixedImageContainer.getHeight()
                : fixedImageContainer.getPrefHeight();
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(width, height);
        fixedImageContainer.setClip(clip);
    }

    /**
     * Updates the canvas size to match the container.
     */
    private void updateCanvasSize() {
        double width = fixedImageContainer.getPrefWidth();
        double height = fixedImageContainer.getHeight() > 0 ? fixedImageContainer.getHeight()
                : fixedImageContainer.getPrefHeight();
        overlayCanvas.setWidth(width);
        overlayCanvas.setHeight(height);
        mainImageView.setFitWidth(width);
        mainImageView.setFitHeight(height);
        redrawOverlays();
    }

    /**
     * Sets up zoom and pan functionality with proper clamping.
     */
    private void setupZoomAndPan() {
        // Mouse wheel zoom - only zoom the image and overlay, not the container
        fixedImageContainer.setOnScroll(event -> {
            double deltaY = event.getDeltaY();
            double oldZoom = zoomFactor;

            if (deltaY < 0) {
                zoomFactor *= 0.9;
            } else {
                zoomFactor *= 1.1;
            }

            // Enforce minimum zoom of 1.0 (no zooming below 1.0)
            zoomFactor = Math.max(1.0, Math.min(5.0, zoomFactor));

            // If zoom changed, apply it and re-clamp position
            if (zoomFactor != oldZoom) {
                applyZoom();
                clampImagePosition();
            }

            event.consume();
        });

        // Mouse drag for panning - only when zoomed and with proper clamping
        final double[] lastMousePos = new double[2];
        fixedImageContainer.setOnMousePressed(event -> {
            lastMousePos[0] = event.getX();
            lastMousePos[1] = event.getY();
            if (zoomFactor > 1.0) {
                fixedImageContainer.setCursor(javafx.scene.Cursor.MOVE);
            }
        });

        fixedImageContainer.setOnMouseDragged(event -> {
            // Only allow panning when zoomed
            if (zoomFactor <= 1.0)
                return;

            // Compute proper drag deltas (don't invert)
            double dx = event.getX() - lastMousePos[0];
            double dy = event.getY() - lastMousePos[1];

            // Apply deltas to current translation
            double newTranslateX = mainImageView.getTranslateX() + dx;
            double newTranslateY = mainImageView.getTranslateY() + dy;

            // Clamp translation to keep image covering viewport
            newTranslateX = clampTranslation(newTranslateX, true);
            newTranslateY = clampTranslation(newTranslateY, false);

            // Apply clamped translation to both image and overlay
            mainImageView.setTranslateX(newTranslateX);
            mainImageView.setTranslateY(newTranslateY);
            overlayCanvas.setTranslateX(newTranslateX);
            overlayCanvas.setTranslateY(newTranslateY);

            // Update last mouse position
            lastMousePos[0] = event.getX();
            lastMousePos[1] = event.getY();
        });

        fixedImageContainer.setOnMouseReleased(event -> {
            fixedImageContainer.setCursor(javafx.scene.Cursor.DEFAULT);
        });

        // Double-click to reset zoom and pan
        fixedImageContainer.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                resetZoom();
                event.consume();
            }
        });
    }

    /**
     * Clamps translation values to ensure image always covers the viewport.
     * 
     * @param translation the proposed translation value
     * @param isX         true for X-axis, false for Y-axis
     * @return clamped translation value
     */
    private double clampTranslation(double translation, boolean isX) {
        if (mainImageView.getImage() == null)
            return translation;

        // Get actual displayed image dimensions
        double imageWidth = mainImageView.getImage().getWidth();
        double imageHeight = mainImageView.getImage().getHeight();

        // Calculate scaled dimensions
        double scaledWidth = imageWidth * zoomFactor;
        double scaledHeight = imageHeight * zoomFactor;

        // Container dimensions
        double containerWidth = fixedImageContainer.getPrefWidth();
        double containerHeight = fixedImageContainer.getHeight() > 0 ? fixedImageContainer.getHeight()
                : fixedImageContainer.getPrefHeight();

        if (isX) {
            // For X: ensure image always covers the viewport width
            double maxTranslateX = Math.max(0, (scaledWidth - containerWidth) / 2);
            return Math.max(-maxTranslateX, Math.min(maxTranslateX, translation));
        } else {
            // For Y: ensure image always covers the viewport height
            double maxTranslateY = Math.max(0, (scaledHeight - containerHeight) / 2);
            return Math.max(-maxTranslateY, Math.min(maxTranslateY, translation));
        }
    }

    /**
     * Re-clamps the image position after zoom changes.
     */
    private void clampImagePosition() {
        double clampedX = clampTranslation(mainImageView.getTranslateX(), true);
        double clampedY = clampTranslation(mainImageView.getTranslateY(), false);

        mainImageView.setTranslateX(clampedX);
        mainImageView.setTranslateY(clampedY);
        overlayCanvas.setTranslateX(clampedX);
        overlayCanvas.setTranslateY(clampedY);
    }

    /**
     * Applies the current zoom factor to the image and overlay.
     */
    private void applyZoom() {
        // Only scale the image and overlay, not the container
        mainImageView.setScaleX(zoomFactor);
        mainImageView.setScaleY(zoomFactor);
        overlayCanvas.setScaleX(zoomFactor);
        overlayCanvas.setScaleY(zoomFactor);

        // Update zoom label
        int zoomPercent = (int) Math.round(zoomFactor * 100);
        zoomLabel.setText(zoomPercent + "%");

        // Redraw overlays at new scale
        redrawOverlays();
    }

    /**
     * Resets zoom to 100% and centers the image.
     */
    private void resetZoom() {
        zoomFactor = 1.0;
        // Reset both zoom and pan for image and overlay
        mainImageView.setScaleX(1.0);
        mainImageView.setScaleY(1.0);
        mainImageView.setTranslateX(0);
        mainImageView.setTranslateY(0);
        overlayCanvas.setScaleX(1.0);
        overlayCanvas.setScaleY(1.0);
        overlayCanvas.setTranslateX(0);
        overlayCanvas.setTranslateY(0);

        // Update zoom label
        zoomLabel.setText("100%");

        // Redraw overlays
        redrawOverlays();
    }

    /**
     * Redraws the overlay graphics for the current image.
     */
    private void redrawOverlays() {
        if (currentImageIndex >= 0 && currentImageIndex < images.size()) {
            Path currentImage = images.get(currentImageIndex);

            // Get SearchResponse using role-based mapping
            SearchResponse searchResponse = getSearchResponseForCurrentImage();

            // Clear and redraw overlays
            overlayCanvas.getGraphicsContext2D().clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
            if (mainImageView.getImage() != null) {
                OverlayUtils.drawBoundingBoxes(overlayCanvas.getGraphicsContext2D(), currentImage, searchResponse,
                        overlayCanvas.getWidth(), overlayCanvas.getHeight(),
                        mainImageView.getImage().getWidth(), mainImageView.getImage().getHeight());
            } else {
                OverlayUtils.drawBoundingBoxes(overlayCanvas.getGraphicsContext2D(), currentImage, searchResponse,
                        overlayCanvas.getWidth(), overlayCanvas.getHeight());
            }
        }
    }

    /**
     * Gets the SearchResponse for the current image using role-based mapping.
     * 
     * @return SearchResponse for current image or null if not found
     */
    private SearchResponse getSearchResponseForCurrentImage() {
        if (currentImageIndex < 0 || currentImageIndex >= images.size()) {
            return null;
        }

        Path currentImage = images.get(currentImageIndex);
        return this.result.getImageAnalysisData().get(currentImage);

    }

    /**
     * Creates the current image data section.
     * 
     * @return VBox containing current image data
     */
    private VBox createCurrentImageDataSection() {
        VBox section = new VBox(15);
        section.getStyleClass().add("gallery-sidebar");
        section.setPrefWidth(250);
        section.setPadding(new Insets(15, 15, 20, 15)); // Add bottom padding for consistent spacing

        Label title = new Label("Current Image Data");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        section.getChildren().add(title);
        return section;
    }

    /**
     * Loads and displays the current image with overlays.
     */
    private void loadCurrentImage() {
        if (currentImageIndex >= 0 && currentImageIndex < images.size()) {
            Path currentImage = images.get(currentImageIndex);

            // Load main image
            try {
                Image image = new Image(currentImage.toUri().toString());
                mainImageView.setImage(image);
            } catch (Exception e) {
                // Handle image loading error
            }

            // Get SearchResponse data using role-based mapping
            SearchResponse searchResponse = getSearchResponseForCurrentImage();

            // Draw overlays using SearchResponse data with current image dimensions
            if (mainImageView.getImage() != null) {
                OverlayUtils.drawBoundingBoxes(overlayCanvas.getGraphicsContext2D(), currentImage, searchResponse,
                        overlayCanvas.getWidth(), overlayCanvas.getHeight(),
                        mainImageView.getImage().getWidth(), mainImageView.getImage().getHeight());
            } else {
                OverlayUtils.drawBoundingBoxes(overlayCanvas.getGraphicsContext2D(), currentImage, searchResponse,
                        overlayCanvas.getWidth(), overlayCanvas.getHeight());
            }

            Platform.runLater(() -> {
                // Update current image data section
                updateCurrentImageDataSection(currentImage, searchResponse);
                // Update page label
                updatePageLabel();
            });
        }
    }

    /**
     * Updates the current image data section with SearchResponse data.
     * 
     * @param currentImage   the current image path
     * @param searchResponse the SearchResponse data
     */
    private void updateCurrentImageDataSection(Path currentImage, SearchResponse searchResponse) {
        VBox section = new VBox(15);
        section.getStyleClass().add("gallery-sidebar");
        section.setPrefWidth(300); // Increased width for more data
        section.setPadding(new Insets(15, 15, 25, 15)); // Add extra bottom padding so textarea doesn't touch window
                                                        // bottom

        Label title = new Label("Current Image Data");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        //

        Label imageNameLabel = new Label("Image: " + currentImage.getFileName().toString());
        imageNameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Full SearchResponse JSON data - make it fill available space
        TextArea jsonArea = new TextArea();
        jsonArea.setEditable(false);
        jsonArea.setWrapText(true);
        jsonArea.getStyleClass().add("json-area");
        // Remove fixed height constraints to allow growth
        VBox.setVgrow(jsonArea, Priority.ALWAYS);

        try {
            if (searchResponse != null) {
                // Use Protobuf JSON conversion instead of Jackson to avoid serialization issues
                String jsonString = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .print(searchResponse); // Use default camelCase formatting to match Analysis Data
                jsonArea.setText(jsonString);
            } else {
                jsonArea.setText("No analysis data available for this image.");
            }
        } catch (Exception e) {
            jsonArea.setText("Error displaying analysis data: " + e.getMessage());
        }

        section.getChildren().addAll(title, imageNameLabel, jsonArea);

        // Update the right panel
        if (galleryStage.getScene() != null && galleryStage.getScene().getRoot() != null) {
            BorderPane root = (BorderPane) galleryStage.getScene().getRoot();
            root.setRight(section);
        }
    }

    /**
     * Navigates to the next or previous image.
     * 
     * @param direction -1 for previous, 1 for next
     */
    private void navigateImage(int direction) {
        int newIndex = currentImageIndex + direction;
        if (newIndex >= 0 && newIndex < images.size()) {
            currentImageIndex = newIndex;
            loadCurrentImage();
            updateNavigationButtons();
            updateThumbnailSelection();
            resetZoom();
        }
    }

    /**
     * Updates the navigation button states.
     */
    private void updateNavigationButtons() {
        if (prevButton != null && nextButton != null) {
            prevButton.setDisable(currentImageIndex <= 0);
            nextButton.setDisable(currentImageIndex >= images.size() - 1);
        }
    }

    /**
     * Updates the page label.
     */
    private void updatePageLabel() {
        if (pageLabel != null) {
            pageLabel.setText((currentImageIndex + 1) + " / " + images.size());
        }
    }

    /**
     * Updates the thumbnail selection highlighting.
     */
    private void updateThumbnailSelection() {
        // Update the thumbnail gallery to highlight the current image
        if (galleryStage.getScene() != null && galleryStage.getScene().getRoot() != null) {
            BorderPane root = (BorderPane) galleryStage.getScene().getRoot();
            VBox newThumbnailSection = createThumbnailSection();
            root.setLeft(newThumbnailSection);
        }
    }

    // Helper methods for parsing plate data
    private String extractPlateText(String plateInfo) {
        if (plateInfo == null || plateInfo.isEmpty())
            return "";
        String[] parts = plateInfo.split(" ");
        return parts.length > 0 ? parts[0] : "";
    }

    private String extractJurisdiction(String plateInfo) {
        if (plateInfo == null || plateInfo.isEmpty())
            return "";
        String[] parts = plateInfo.split(" ");
        for (String part : parts) {
            if (part.contains("-")) {
                return part;
            }
        }
        return "";
    }

    private String extractCategory(String plateInfo) {
        if (plateInfo == null || plateInfo.isEmpty())
            return "";
        String[] parts = plateInfo.split(" ");
        for (String part : parts) {
            if (!part.contains("-") && !part.contains("%") && !part.equals(parts[0])) {
                return part;
            }
        }
        return "";
    }

    private String extractConfidence(String plateInfo) {
        if (plateInfo == null || plateInfo.isEmpty())
            return "";
        String[] parts = plateInfo.split(" ");
        for (String part : parts) {
            if (part.contains("%")) {
                return part;
            }
        }
        return "";
    }

    public void show() {
        if (galleryStage != null) {
            galleryStage.showAndWait();
        }
    }

    /**
     * Closes the gallery window and clears all caches.
     */
    public void close() {
        if (galleryStage != null) {
            galleryStage.close();
        }
    }
}