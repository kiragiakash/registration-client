package io.mosip.registration.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.idvalidator.spi.PridValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.api.docscanner.DocScannerUtil;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.controller.vo.PacketStatusVO;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

@Controller
public class QrCodePopUpViewController extends BaseController implements Initializable, Runnable, ThreadFactory {

    private static final Logger LOGGER = AppConfig.getLogger(QrCodePopUpViewController.class);

    @FXML
    private GridPane captureWindow;

    @FXML
    private TextField scannedTextField;

    @FXML
    private ComboBox<String> availableWebcams;

    @FXML
    private Button doneButton;

    @Value("${mosip.doc.stage.width:400}")
    private int width;

    @Value("${mosip.doc.stage.height:400}")
    private int height;

    @Autowired
    private GenericController genericController;

    @Autowired
    private PridValidator<String> pridValidatorImpl;

    public Stage getPopupStage() {
        return popupStage;
    }

    private Stage popupStage;

    private WebcamPanel panel = null;

    private Webcam webcam = null;

    private Executor executor = Executors.newSingleThreadExecutor(this);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //Check if there are any cameras connected
        if (Webcam.getWebcams().isEmpty()) {
            LOGGER.error(RegistrationConstants.USER_REG_SCAN_EXP);
            generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICES_DETECTED));
        } else {
            availableWebcams.getItems().addAll(Webcam.getWebcams().stream().map(s -> s.getName()).collect(Collectors.toList()));
            availableWebcams.getSelectionModel().select(0);
            availableWebcams.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
                Webcam oldWebcam = Webcam.getWebcamByName(oldValue);
                oldWebcam.close();
                initWebcam(newValue);
            });
            initWebcam(availableWebcams.getSelectionModel().getSelectedItem());
        }
    }

    /**
     * This method will open popup to scan
     *
     * @param title
     */
    public void init(String title) {
        try {
            LOGGER.info("Loading QR code popup page : {}", RegistrationConstants.QR_CODE_PAGE);
            Parent scanPopup = BaseController.load(getClass().getResource(RegistrationConstants.QR_CODE_PAGE));

            LOGGER.info("Setting doc screen width :{}, height: {}", width, height);
            Scene scene = new Scene(scanPopup, width, height);

            scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
            popupStage = new Stage();
            popupStage.setResizable(true);
            popupStage.setMaximized(false);
            popupStage.setScene(scene);
            popupStage.initModality(Modality.WINDOW_MODAL);
            popupStage.initOwner(fXComponents.getStage());
            popupStage.setTitle(title);
            popupStage.setOnCloseRequest((e) -> {
                exitWindow(e);
            });
            popupStage.show();

            LOGGER.debug("qr scan screen launched");
            LOGGER.info("Opening pop-up screen to qr code scan for loading pre-registration id");

        } catch (IOException exception) {
            LOGGER.error(RegistrationConstants.USER_REG_SCAN_EXP, exception);
            generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_QR_SCAN_POPUP));
        }
    }

    private void initWebcam(String name) {
        doneButton.setDisable(true);

        Dimension size = WebcamResolution.VGA.getSize();
        webcam = Webcam.getWebcamByName(name);
        if (!webcam.isOpen()) {

            webcam.setViewSize(size);

            panel = new WebcamPanel(webcam);
            panel.setPreferredSize(size);
            panel.setFPSDisplayed(true);

            final SwingNode swingNode = new SwingNode();
            swingNode.setContent(panel);

            captureWindow.getChildren().add(swingNode);
            executor.execute(this);
        }
    }

    @Override
    public void run() {
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            Result result = null;
            BufferedImage image = null;

            if (webcam.isOpen()) {
                if ((image = webcam.getImage()) == null) {
                    continue;
                }
            }

            try {
                LuminanceSource source = new BufferedImageLuminanceSource(image);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                result = new MultiFormatReader().decode(bitmap);
            } catch (Exception e) {
                LOGGER.error(RegistrationConstants.USER_REG_SCAN_EXP, e);
                generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_QR_SCAN_POPUP);
            }

            if (result != null) {
                boolean isValid;
                try {
                    isValid = pridValidatorImpl.validateId(result.getText());
                } catch (InvalidIDException invalidIDException) {
                    isValid = false;
                }

                if (isValid) {
                    this.scannedTextField.setText(result.getText());
                    doneButton.setDisable(false);
                } else {
                    generateAlertLanguageSpecific(RegistrationConstants.ERROR, RegistrationUIConstants.PRE_REG_ID_NOT_VALID);
                }
            }
        } while (true);
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, "Scan QR Code Thread");
        t.setDaemon(true);
        return t;
    }

    @FXML
    public void done() {
        if (this.scannedTextField.getText() != null) {
            this.genericController.getRegistrationNumberTextField().setText(this.scannedTextField.getText());
        }
        stopStreaming();
        clearSelection();
        popupStage.close();
        generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.QR_CODE_SCAN_SUCCESS));
        LOGGER.debug("Scanning QR code completed");
    }

    /**
     * event class to exit from present pop up window.
     *
     * @param event
     */
    public void exitWindow(WindowEvent event) {
        LOGGER.info("Calling exit window to close the popup");
        stopStreaming();
        clearSelection();
        popupStage.close();

        LOGGER.info("Scan Popup is closed");
    }

    private void stopStreaming() {
        try {
            if (webcam.isOpen()) {
                webcam.close();
            }
        } finally {
            webcam.close();
        }
    }

    private void clearSelection() {
        this.scannedTextField.setText(null);
    }
}
