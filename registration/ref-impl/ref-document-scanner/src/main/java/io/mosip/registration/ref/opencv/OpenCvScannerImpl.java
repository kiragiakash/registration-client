package io.mosip.registration.ref.opencv;

import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.DocScannerService;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


@Component
public class OpenCvScannerImpl implements DocScannerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCvScannerImpl.class);
    private static final String SERVICE_NAME = "OpenCV";
    private static final String DELIMITER = ":";

    public OpenCvScannerImpl() {
        OpenCV.loadShared();
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public BufferedImage scan(DocScanDevice docScanDevice) {
        int width = 980;
        int height = 540;

        int index = Integer.parseInt(docScanDevice.getName().split(DELIMITER)[1]);
        VideoCapture capture = new VideoCapture(index);
        capture.set(Videoio.CAP_PROP_BUFFERSIZE, 1);

        Mat frame = new Mat(new Size(width, height), CvType.CV_8UC3);
        if (capture.isOpened()) {
            capture.read(frame);
            try {
                return mat2Img(frame);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public List<DocScanDevice> getConnectedDevices() {
        var deviceIndexList = returnCameraIndexes();

        List<DocScanDevice> devices = new ArrayList<>();
        for (Integer index : deviceIndexList) {
            VideoCapture capture = new VideoCapture(index);
            DocScanDevice docScanDevice = new DocScanDevice();
            docScanDevice.setDeviceType(DeviceType.CAMERA);
            docScanDevice.setName(capture.getBackendName() + DELIMITER + index);
            docScanDevice.setServiceName(getServiceName());
            docScanDevice.setId(SERVICE_NAME + DELIMITER + capture.getBackendName());
            devices.add(docScanDevice);
            capture.release();
        }
        return devices;
    }

    @Override
    public void stop(DocScanDevice docScanDevice) {
        int index = Integer.parseInt(docScanDevice.getName().split(DELIMITER)[1]);
        VideoCapture capture = new VideoCapture(index);
        capture.release();
    }

    public BufferedImage mat2Img(Mat mat) throws IOException {
        MatOfByte bytes = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, bytes);
        InputStream inputStream = new ByteArrayInputStream(bytes.toArray());
        return ImageIO.read(inputStream);
    }

    private List<Integer> returnCameraIndexes() {
        var cameraIndexes = new ArrayList<Integer>();
        var iterator = 0;
        var end = 10;
        while (end > 0) {
            var cap = new VideoCapture(iterator);
            if (cap.isOpened()) {
                cameraIndexes.add(iterator);
                cap.release();
            }
            iterator++;
            end--;
        }

        return cameraIndexes;
    }
}