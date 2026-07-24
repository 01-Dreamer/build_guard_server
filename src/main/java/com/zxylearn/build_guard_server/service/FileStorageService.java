package com.zxylearn.build_guard_server.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.mapper.FileResourceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileStorageService {
    private final FileResourceMapper fileResourceMapper;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;
    private final Path localRoot;
    private final String publicBaseUrl;

    public FileStorageService(FileResourceMapper fileResourceMapper,
                              @Value("${aliyun.oss.endpoint:}") String endpoint,
                              @Value("${aliyun.oss.accessKeyId:}") String accessKeyId,
                              @Value("${aliyun.oss.accessKeySecret:}") String accessKeySecret,
                              @Value("${aliyun.oss.bucketName:buildguard-dev}") String bucketName,
                              @Value("${buildguard.storage.local-root:data/uploads}") String localRoot,
                              @Value("${buildguard.storage.public-base-url:http://127.0.0.1:18080}") String publicBaseUrl) {
        this.fileResourceMapper = fileResourceMapper;
        this.endpoint = trim(endpoint);
        this.accessKeyId = trim(accessKeyId);
        this.accessKeySecret = trim(accessKeySecret);
        this.bucketName = trim(bucketName);
        this.localRoot = Path.of(localRoot).toAbsolutePath().normalize();
        this.publicBaseUrl = trim(publicBaseUrl).replaceAll("/+$", "");
    }

    public FileResource saveMultipart(MultipartFile file, String bizType, Long bizId, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        String objectKey = buildObjectKey(prefix, file.getOriginalFilename());
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (ossEnabled()) {
            uploadOss(file, objectKey, contentType);
        } else {
            saveLocal(file, objectKey);
        }

        FileResource resource = new FileResource();
        resource.setBucket(bucketName);
        resource.setObjectKey(objectKey);
        resource.setUrl(ossEnabled() ? ossPublicUrl(objectKey) : null);
        resource.setFileName(file.getOriginalFilename());
        resource.setContentType(contentType);
        resource.setSizeBytes(file.getSize());
        resource.setBizType(bizType);
        resource.setBizId(bizId);
        resource.setCreatedAt(LocalDateTime.now());
        fileResourceMapper.insert(resource);
        if (!ossEnabled()) {
            resource.setUrl(publicBaseUrl + "/api/files/" + resource.getId());
            fileResourceMapper.updateById(resource);
        }
        return resource;
    }

    public FileResource savePath(Path sourcePath, String contentType, String fileName, String bizType, Long bizId, String prefix) {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new BusinessException(404, "源文件不存在");
        }
        String objectKey = buildObjectKey(prefix, fileName == null ? sourcePath.getFileName().toString() : fileName);
        String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        long size;
        try {
            size = Files.size(sourcePath);
        } catch (IOException exception) {
            throw new BusinessException(500, "读取源文件失败");
        }

        if (ossEnabled()) {
            uploadPathToOss(sourcePath, objectKey, safeContentType, size);
        } else {
            copyLocal(sourcePath, objectKey);
        }

        FileResource resource = new FileResource();
        resource.setBucket(bucketName);
        resource.setObjectKey(objectKey);
        resource.setUrl(ossEnabled() ? ossPublicUrl(objectKey) : null);
        resource.setFileName(fileName == null ? sourcePath.getFileName().toString() : fileName);
        resource.setContentType(safeContentType);
        resource.setSizeBytes(size);
        resource.setBizType(bizType);
        resource.setBizId(bizId);
        resource.setCreatedAt(LocalDateTime.now());
        fileResourceMapper.insert(resource);
        if (!ossEnabled()) {
            resource.setUrl(publicBaseUrl + "/api/files/" + resource.getId());
            fileResourceMapper.updateById(resource);
        }
        return resource;
    }

    public FileResource saveBytes(byte[] bytes, String contentType, String fileName, String bizType, Long bizId, String prefix) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(400, "文件内容不能为空");
        }
        String objectKey = buildObjectKey(prefix, fileName);
        String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;

        if (ossEnabled()) {
            uploadBytesToOss(bytes, objectKey, safeContentType);
        } else {
            saveBytesLocal(bytes, objectKey);
        }

        FileResource resource = new FileResource();
        resource.setBucket(bucketName);
        resource.setObjectKey(objectKey);
        resource.setUrl(ossEnabled() ? ossPublicUrl(objectKey) : null);
        resource.setFileName(fileName);
        resource.setContentType(safeContentType);
        resource.setSizeBytes((long) bytes.length);
        resource.setBizType(bizType);
        resource.setBizId(bizId);
        resource.setCreatedAt(LocalDateTime.now());
        fileResourceMapper.insert(resource);
        if (!ossEnabled()) {
            resource.setUrl(publicBaseUrl + "/api/files/" + resource.getId());
            fileResourceMapper.updateById(resource);
        }
        return resource;
    }

    public FileResource findById(long id) {
        FileResource resource = fileResourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(404, "文件不存在");
        }
        return resource;
    }

    public Resource localResource(FileResource resource) {
        Path path = localRoot.resolve(resource.getObjectKey()).normalize();
        if (!path.startsWith(localRoot) || !Files.exists(path)) {
            throw new BusinessException(404, "本地文件不存在");
        }
        return new FileSystemResource(path);
    }

    public java.util.List<FileResource> listByBiz(String bizType, Long bizId) {
        return fileResourceMapper.selectList(Wrappers.<FileResource>lambdaQuery()
                .eq(FileResource::getBizType, bizType)
                .eq(bizId != null, FileResource::getBizId, bizId)
                .orderByDesc(FileResource::getId));
    }

    private void uploadOss(MultipartFile file, String objectKey, String contentType) {
        OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream input = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            oss.putObject(bucketName, objectKey, input, metadata);
        } catch (IOException exception) {
            throw new BusinessException(500, "上传OSS失败");
        } finally {
            oss.shutdown();
        }
    }

    private void uploadPathToOss(Path sourcePath, String objectKey, String contentType, long size) {
        OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream input = Files.newInputStream(sourcePath)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);
            oss.putObject(bucketName, objectKey, input, metadata);
        } catch (IOException exception) {
            throw new BusinessException(500, "上传OSS失败");
        } finally {
            oss.shutdown();
        }
    }

    private void uploadBytesToOss(byte[] bytes, String objectKey, String contentType) {
        OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            oss.putObject(bucketName, objectKey, input, metadata);
        } catch (IOException exception) {
            throw new BusinessException(500, "上传OSS失败");
        } finally {
            oss.shutdown();
        }
    }

    private void saveLocal(MultipartFile file, String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }
        try {
            Files.createDirectories(path.getParent());
            file.transferTo(path);
        } catch (IOException exception) {
            throw new BusinessException(500, "保存本地文件失败");
        }
    }

    private void copyLocal(Path sourcePath, String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }
        try {
            Files.createDirectories(path.getParent());
            Files.copy(sourcePath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(500, "保存本地文件失败");
        }
    }

    private void saveBytesLocal(byte[] bytes, String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new BusinessException(500, "保存本地文件失败");
        }
    }

    private String buildObjectKey(String prefix, String originalFilename) {
        String safePrefix = trim(prefix).replaceAll("^/+|/+$", "");
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = extension(originalFilename);
        return safePrefix + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,12}") ? ext : "";
    }

    private String ossPublicUrl(String objectKey) {
        String normalizedEndpoint = endpoint.startsWith("http://") || endpoint.startsWith("https://")
                ? endpoint
                : "https://" + endpoint;
        return normalizedEndpoint.replace("://", "://" + bucketName + ".") + "/" + objectKey;
    }

    private boolean ossEnabled() {
        return !endpoint.isBlank() && !accessKeyId.isBlank() && !accessKeySecret.isBlank() && !bucketName.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
