package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.CameraView;
import com.zxylearn.build_guard_server.entity.FileResource;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CameraVideoRecordingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraVideoRecordingService.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final PlatformPageService platformPageService;
    private final FileStorageService fileStorageService;
    private final boolean enabled;
    private final int segmentSeconds;
    private final int fps;
    private final int maxCamerasPerCycle;
    private final int streamReadTimeoutMs;
    private final Path tempRoot;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final ExecutorService recorderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "camera-video-recorder");
        thread.setDaemon(true);
        return thread;
    });

    public CameraVideoRecordingService(PlatformPageService platformPageService,
                                       FileStorageService fileStorageService,
                                       @Value("${buildguard.camera-recording.enabled:true}") boolean enabled,
                                       @Value("${buildguard.camera-recording.segment-seconds:300}") int segmentSeconds,
                                       @Value("${buildguard.camera-recording.fps:5}") int fps,
                                       @Value("${buildguard.camera-recording.max-cameras-per-cycle:4}") int maxCamerasPerCycle,
                                       @Value("${buildguard.camera-recording.stream-read-timeout-ms:3000}") int streamReadTimeoutMs,
                                       @Value("${buildguard.camera-recording.temp-root:data/camera-video-segments}") String tempRoot) {
        this.platformPageService = platformPageService;
        this.fileStorageService = fileStorageService;
        this.enabled = enabled;
        this.segmentSeconds = Math.max(10, segmentSeconds);
        this.fps = Math.max(1, Math.min(15, fps));
        this.maxCamerasPerCycle = Math.max(1, maxCamerasPerCycle);
        this.streamReadTimeoutMs = Math.max(1000, streamReadTimeoutMs);
        this.tempRoot = Path.of(tempRoot).toAbsolutePath().normalize();
    }

    @Scheduled(
            initialDelayString = "${buildguard.camera-recording.initial-delay-ms:30000}",
            fixedDelayString = "${buildguard.camera-recording.check-interval-ms:1000}"
    )
    public void recordCameraSegments() {
        if (!enabled || !recording.compareAndSet(false, true)) {
            return;
        }
        recorderExecutor.submit(this::recordCameraSegmentsSafely);
    }

    @PreDestroy
    public void close() {
        recorderExecutor.shutdownNow();
    }

    private void recordCameraSegmentsSafely() {
        try {
            PageResult<CameraView> cameras = platformPageService.cameras(1, maxCamerasPerCycle);
            for (CameraView camera : cameras.records()) {
                if (!shouldRecord(camera)) {
                    continue;
                }
                try {
                    recordOneSegment(camera);
                } catch (Exception exception) {
                    LOGGER.warn("camera video segment skipped camera={} reason={}", camera.code(), exception.getMessage());
                }
            }
        } finally {
            recording.set(false);
        }
    }

    private boolean shouldRecord(CameraView camera) {
        return camera != null
                && camera.id() != null
                && camera.code() != null
                && Integer.valueOf(1).equals(camera.enabled())
                && Integer.valueOf(1).equals(camera.onlineStatus());
    }

    private void recordOneSegment(CameraView camera) throws IOException {
        Files.createDirectories(tempRoot);
        LocalDateTime start = LocalDateTime.now();
        String filename = camera.code() + "_" + FILE_TIME.format(start) + ".mp4";
        Path outputPath = tempRoot.resolve(filename).normalize();
        if (!outputPath.startsWith(tempRoot)) {
            throw new IOException("非法视频分片路径");
        }

        int frameCount = 0;
        try {
            String streamUrl = camera.streamUrl();
            if (streamUrl != null && streamUrl.startsWith("http")) {
                frameCount = recordFromMjpegStream(camera, streamUrl, outputPath);
            }
            if (frameCount == 0) {
                frameCount = recordFromSnapshots(camera, outputPath);
            }

            if (frameCount < Math.max(2, fps)) {
                Files.deleteIfExists(outputPath);
                LOGGER.warn("camera video segment has insufficient frames camera={} frames={}", camera.code(), frameCount);
                return;
            }

            LocalDateTime end = LocalDateTime.now();
            String finalName = camera.code() + "_" + FILE_TIME.format(start) + "_" + FILE_TIME.format(end) + ".mp4";
            FileResource resource = fileStorageService.savePath(
                    outputPath,
                    "video/mp4",
                    finalName,
                    "camera_video",
                    camera.id(),
                    "camera/video/" + camera.code() + "/segments"
            );
            LOGGER.info("camera video segment uploaded camera={} file={} frames={} objectKey={}",
                    camera.code(), resource.getFileName(), frameCount, resource.getObjectKey());
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    private int recordFromMjpegStream(CameraView camera, String streamUrl, Path outputPath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(streamReadTimeoutMs);
        connection.setRequestProperty("Accept", "multipart/x-mixed-replace,image/jpeg,*/*");
        connection.connect();
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IOException("摄像头流返回异常: " + connection.getResponseCode());
        }

        long deadline = System.currentTimeMillis() + segmentSeconds * 1000L;
        long nextEncodeAt = System.currentTimeMillis();
        int encoded = 0;
        FrameSize frameSize = null;
        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(outputPath.toFile(), fps);
        boolean finished = false;
        try (InputStream raw = new BufferedInputStream(connection.getInputStream())) {
            while (System.currentTimeMillis() < deadline) {
                byte[] jpeg = readJpegFrame(raw);
                if (jpeg.length == 0) {
                    break;
                }
                long now = System.currentTimeMillis();
                if (now < nextEncodeAt) {
                    continue;
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
                if (image == null) {
                    continue;
                }
                if (frameSize == null) {
                    frameSize = FrameSize.from(image);
                }
                encoder.encodeImage(normalizeFrame(image, frameSize));
                encoded++;
                nextEncodeAt = now + 1000L / fps;
            }
            encoder.finish();
            finished = true;
        } catch (IOException exception) {
            if (!finished) {
                try {
                    encoder.finish();
                    finished = true;
                } catch (IOException finishException) {
                    exception.addSuppressed(finishException);
                }
            }
            if (encoded == 0) {
                throw exception;
            }
            LOGGER.warn("camera stream ended early camera={} frames={} reason={}", camera.code(), encoded, exception.getMessage());
        } finally {
            connection.disconnect();
        }
        return encoded;
    }

    private int recordFromSnapshots(CameraView camera, Path outputPath) throws IOException {
        long deadline = System.currentTimeMillis() + segmentSeconds * 1000L;
        long frameIntervalMs = 1000L / fps;
        int encoded = 0;
        FrameSize frameSize = null;
        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(outputPath.toFile(), fps);
        while (System.currentTimeMillis() < deadline) {
            PlatformPageService.CameraSnapshot snapshot = platformPageService.cameraSnapshot(camera.code()).orElse(null);
            if (snapshot != null && Files.isRegularFile(snapshot.path())) {
                BufferedImage image = ImageIO.read(snapshot.path().toFile());
                if (image != null) {
                    if (frameSize == null) {
                        frameSize = FrameSize.from(image);
                    }
                    encoder.encodeImage(normalizeFrame(image, frameSize));
                    encoded++;
                }
            }
            sleep(frameIntervalMs);
        }
        encoder.finish();
        return encoded;
    }

    private byte[] readJpegFrame(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256 * 1024);
        int previous = -1;
        int current;
        boolean started = false;
        int maxBytes = 8 * 1024 * 1024;

        while ((current = input.read()) != -1) {
            if (!started) {
                if (previous == 0xFF && current == 0xD8) {
                    started = true;
                    buffer.write(0xFF);
                    buffer.write(0xD8);
                }
                previous = current;
                continue;
            }

            buffer.write(current);
            if (buffer.size() > maxBytes) {
                throw new IOException("摄像头帧过大");
            }
            if (previous == 0xFF && current == 0xD9) {
                return buffer.toByteArray();
            }
            previous = current;
        }
        return new byte[0];
    }

    private BufferedImage normalizeFrame(BufferedImage source, FrameSize frameSize) {
        BufferedImage target = new BufferedImage(frameSize.width(), frameSize.height(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, frameSize.width(), frameSize.height(), null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record FrameSize(int width, int height) {
        private static FrameSize from(BufferedImage image) {
            int width = Math.max(2, image.getWidth());
            int height = Math.max(2, image.getHeight());
            if (width % 2 != 0) {
                width--;
            }
            if (height % 2 != 0) {
                height--;
            }
            return new FrameSize(width, height);
        }
    }
}
