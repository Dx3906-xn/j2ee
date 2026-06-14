package com.docmgr.service;

import com.docmgr.entity.*;
import com.docmgr.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileRepository fileRepo;
    private final FolderRepository folderRepo;
    private final RecentAccessRepository recentRepo;
    private final ShareRepository shareRepo;
    private final Path uploadDir;

    public FileService(FileRepository fileRepo, FolderRepository folderRepo,
                       RecentAccessRepository recentRepo, ShareRepository shareRepo,
                       @Value("${file.upload-dir}") String uploadDir) {
        this.fileRepo = fileRepo;
        this.folderRepo = folderRepo;
        this.recentRepo = recentRepo;
        this.shareRepo = shareRepo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try { Files.createDirectories(this.uploadDir); } catch (IOException ignored) {}
    }

    // ---- Folders ----
    public List<Folder> listFolders(Long ownerId, Long parentId) {
        return folderRepo.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, parentId != null ? parentId : 0L);
    }

    public Folder createFolder(String name, Long parentId, Long ownerId) {
        return folderRepo.save(new Folder(name, parentId != null ? parentId : 0L, ownerId));
    }

    @Transactional
    public void deleteFolder(Long id, Long ownerId) {
        Folder folder = folderRepo.findById(id).orElseThrow(() -> new RuntimeException("文件夹不存在"));
        if (!folder.getOwnerId().equals(ownerId)) throw new RuntimeException("无权操作");
        List<FileEntity> files = fileRepo.findByOwnerIdAndIsTrashAndFolderIdOrderByUpdatedAtDesc(ownerId, 0, id);
        files.forEach(f -> { f.setIsTrash(1); fileRepo.save(f); });
        folderRepo.deleteById(id);
    }

    public void renameFolder(Long id, String name, Long ownerId) {
        Folder folder = folderRepo.findById(id).orElseThrow(() -> new RuntimeException("文件夹不存在"));
        if (!folder.getOwnerId().equals(ownerId)) throw new RuntimeException("无权操作");
        folder.setName(name);
        folderRepo.save(folder);
    }

    // ---- Files ----
    public List<FileEntity> listFiles(Long ownerId, Long folderId, String filter) {
        if (folderId == null || folderId == 0) {
            if (filter != null && !filter.equals("all")) {
                return fileRepo.findByOwnerIdAndIsTrashAndFolderIdAndMimeTypeContainingIgnoreCaseOrderByUpdatedAtDesc(ownerId, 0, 0L, filter);
            }
            return fileRepo.findByOwnerIdAndIsTrashAndFolderIdOrderByUpdatedAtDesc(ownerId, 0, 0L);
        }
        if (filter != null && !filter.equals("all")) {
            return fileRepo.findByOwnerIdAndIsTrashAndFolderIdAndMimeTypeContainingIgnoreCaseOrderByUpdatedAtDesc(ownerId, 0, folderId, filter);
        }
        return fileRepo.findByOwnerIdAndIsTrashAndFolderIdOrderByUpdatedAtDesc(ownerId, 0, folderId);
    }

    public FileEntity uploadFile(MultipartFile file, Long folderId, Long ownerId) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = "file-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000000) + ext;
        Path targetPath = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), targetPath);

        FileEntity entity = new FileEntity();
        entity.setName(originalName);
        entity.setFolderId(folderId != null ? folderId : 0L);
        entity.setOwnerId(ownerId);
        entity.setSize(file.getSize());
        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty() || "application/octet-stream".equals(contentType)) {
            contentType = guessMimeType(ext);
        }
        entity.setMimeType(contentType);
        entity.setPath(targetPath.toString());
        return fileRepo.save(entity);
    }

    public FileEntity getFile(Long id, Long ownerId) {
        FileEntity file = fileRepo.findById(id).orElseThrow(() -> new RuntimeException("文件不存在"));
        if (!file.getOwnerId().equals(ownerId)) throw new RuntimeException("无权操作");
        return file;
    }

    public FileEntity getFilePublic(Long id) { return fileRepo.findById(id).orElseThrow(() -> new RuntimeException("文件不存在")); }

    public void recordAccess(Long fileId, Long userId) {
        recentRepo.upsertAccess(fileId, userId);
    }

    @Transactional
    public void deleteFile(Long id, Long ownerId) {
        FileEntity file = getFile(id, ownerId);
        file.setIsTrash(1);
        fileRepo.save(file);
    }

    @Transactional
    public void permanentDelete(Long id, Long ownerId) {
        FileEntity file = fileRepo.findById(id).orElseThrow(() -> new RuntimeException("文件不存在"));
        if (!file.getOwnerId().equals(ownerId)) throw new RuntimeException("无权操作");
        try { Files.deleteIfExists(Paths.get(file.getPath())); } catch (IOException ignored) {}
        fileRepo.deleteById(id);
    }

    public void restoreFile(Long id, Long ownerId) {
        FileEntity file = getFile(id, ownerId);
        file.setIsTrash(0);
        fileRepo.save(file);
    }

    public List<FileEntity> listTrash(Long ownerId) {
        return fileRepo.findByOwnerIdAndIsTrashOrderByUpdatedAtDesc(ownerId, 1);
    }

    @Transactional
    public void emptyTrash(Long ownerId) {
        List<FileEntity> trash = fileRepo.findByOwnerIdAndIsTrashOrderByUpdatedAtDesc(ownerId, 1);
        trash.forEach(f -> {
            try { Files.deleteIfExists(Paths.get(f.getPath())); } catch (IOException ignored) {}
        });
        fileRepo.deleteAll(trash);
    }

    public void renameFile(Long id, String name, Long ownerId) {
        FileEntity file = getFile(id, ownerId);
        file.setName(name);
        fileRepo.save(file);
    }

    public void moveFile(Long id, Long folderId, Long ownerId) {
        FileEntity file = getFile(id, ownerId);
        file.setFolderId(folderId);
        fileRepo.save(file);
    }

    @Transactional
    public FileEntity copyFile(Long id, Long folderId, Long ownerId) throws IOException {
        FileEntity file = getFile(id, ownerId);
        FileEntity copy = new FileEntity();
        copy.setName(file.getName());
        copy.setFolderId(folderId != null ? folderId : 0L);
        copy.setOwnerId(ownerId);
        copy.setSize(file.getSize());
        copy.setMimeType(file.getMimeType());

        String ext = "";
        if (file.getName() != null && file.getName().contains(".")) {
            ext = file.getName().substring(file.getName().lastIndexOf('.'));
        }
        String newStored = "copy-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000000) + ext;
        Path newPath = uploadDir.resolve(newStored);
        Files.copy(Paths.get(file.getPath()), newPath);
        copy.setPath(newPath.toString());
        return fileRepo.save(copy);
    }

    public void toggleFavorite(Long id, Long ownerId) {
        fileRepo.toggleFavorite(id, ownerId);
    }

    public List<FileEntity> listFavorites(Long ownerId) {
        return fileRepo.findByOwnerIdAndIsFavoriteAndIsTrashOrderByUpdatedAtDesc(ownerId, 1, 0);
    }

    public List<FileEntity> listRecent(Long ownerId) {
        List<RecentAccess> raList = recentRepo.findRecentWithFiles(ownerId);
        return raList.stream()
                .map(ra -> fileRepo.findById(ra.getFileId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Map<String, Object> previewFile(Long id, Long ownerId) throws IOException {
        FileEntity file = getFile(id, ownerId);
        recordAccess(id, ownerId);
        Path fp = Paths.get(file.getPath());
        if (!Files.exists(fp)) throw new RuntimeException("文件已被删除");

        String name = file.getName();
        String ext = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase() : "";
        Set<String> textExts = Set.of(".txt", ".md", ".js", ".ts", ".jsx", ".tsx", ".html", ".css",
                ".json", ".xml", ".csv", ".py", ".java", ".c", ".cpp", ".h", ".rb", ".go", ".rs",
                ".yaml", ".yml", ".env", ".gitignore", ".log", ".sh", ".bat");
        if (textExts.contains(ext)) {
            String content = Files.readString(fp);
            if (content.length() > 50000) content = content.substring(0, 50000);
            return Map.of("type", "text", "content", content, "name", file.getName(), "mime_type", file.getMimeType());
        }
        String mime = file.getMimeType();
        if ((mime != null && mime.startsWith("image/")) || 
            (".png".equals(ext) || ".jpg".equals(ext) || ".jpeg".equals(ext) || ".gif".equals(ext) ||
             ".bmp".equals(ext) || ".webp".equals(ext) || ".svg".equals(ext) || ".ico".equals(ext))) {
            return Map.of("type", "image", "url", "/api/files/stream/" + id, "name", file.getName(),
                    "mime_type", mime != null ? mime : "image/" + ext.substring(1));
        }
        // Video support
        if ((mime != null && mime.startsWith("video/")) ||
            (".mp4".equals(ext) || ".webm".equals(ext) || ".ogg".equals(ext) || ".mov".equals(ext) || ".avi".equals(ext))) {
            return Map.of("type", "video", "url", "/api/files/stream/" + id, "name", file.getName(),
                    "mime_type", mime != null ? mime : "video/" + ext.substring(1));
        }
        // Audio support
        if ((mime != null && mime.startsWith("audio/")) ||
            (".mp3".equals(ext) || ".wav".equals(ext) || ".flac".equals(ext) || ".aac".equals(ext) || ".m4a".equals(ext))) {
            return Map.of("type", "audio", "url", "/api/files/stream/" + id, "name", file.getName(),
                    "mime_type", mime != null ? mime : "audio/" + ext.substring(1));
        }
        // PDF support
        if ((mime != null && mime.equals("application/pdf")) || ".pdf".equals(ext)) {
            return Map.of("type", "pdf", "url", "/api/files/stream/" + id, "name", file.getName(),
                    "mime_type", mime != null ? mime : "application/pdf");
        }
        return Map.of("type", "unsupported", "name", file.getName(), "mime_type", mime != null ? mime : "",
                "size", file.getSize());
    }

    public Map<String, Object> search(String q, Long ownerId) {
        List<FileEntity> files = fileRepo.findByOwnerIdAndIsTrashAndNameContainingOrderByUpdatedAtDesc(ownerId, 0, q);
        List<Folder> folders = folderRepo.findByOwnerIdAndNameContainingOrderByNameAsc(ownerId, q);
        List<Team> teams = List.of();
        return Map.of("files", files, "folders", folders, "teams", teams);
    }

    public Share createShare(Long fileId, String expiry, String password) {
        String code = generateShareCode();
        LocalDateTime expiresAt = null;
        if (expiry != null && !expiry.equals("never")) {
            Map<String, Long> expMap = Map.of("1h", 3600000L, "6h", 21600000L, "1d", 86400000L, "7d", 604800000L, "30d", 2592000000L);
            if (expMap.containsKey(expiry)) {
                expiresAt = LocalDateTime.now().plusSeconds(expMap.get(expiry) / 1000);
            }
        }
        Share share = new Share();
        share.setFileId(fileId);
        share.setCode(code);
        share.setPassword(password != null && !password.isEmpty() ? password : null);
        share.setExpiresAt(expiresAt);
        return shareRepo.save(share);
    }

    public Map<String, Object> getShareByCode(String code) {
        Share share = shareRepo.findByCode(code)
            .orElseThrow(() -> new RuntimeException("链接不存在或已过期"));
        
        // Check expiry
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            shareRepo.delete(share);
            throw new RuntimeException("链接已过期");
        }
        
        FileEntity file = fileRepo.findById(share.getFileId())
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        return Map.of(
            "code", share.getCode(),
            "fileName", file.getName(),
            "fileSize", file.getSize(),
            "mimeType", file.getMimeType(),
            "hasPassword", share.getPassword() != null && !share.getPassword().isEmpty()
        );
    }

    public Map<String, Object> verifyShare(String code, String password) {
        Share share = shareRepo.findByCode(code)
            .orElseThrow(() -> new RuntimeException("链接不存在或已过期"));
        
        // Check expiry
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            shareRepo.delete(share);
            throw new RuntimeException("链接已过期");
        }
        
        // Check password
        if (share.getPassword() != null && !share.getPassword().isEmpty()) {
            if (password == null || !password.equals(share.getPassword())) {
                throw new RuntimeException("提取码错误");
            }
        }
        
        FileEntity file = fileRepo.findById(share.getFileId())
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        return Map.of(
            "fileId", file.getId(),
            "fileName", file.getName(),
            "fileSize", file.getSize(),
            "mimeType", file.getMimeType(),
            "streamUrl", "/api/files/stream/" + file.getId()
        );
    }

    private String generateShareCode() {
        byte[] bytes = new byte[6];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Transactional
    public void batchDelete(List<Long> ids, Long ownerId) {
        ids.forEach(id -> {
            fileRepo.findById(id).ifPresent(f -> {
                if (f.getOwnerId().equals(ownerId)) {
                    f.setIsTrash(1);
                    fileRepo.save(f);
                }
            });
        });
    }

    private String guessMimeType(String ext) {
        return switch (ext.toLowerCase()) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            case ".svg" -> "image/svg+xml";
            case ".ico" -> "image/x-icon";
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".mov" -> "video/quicktime";
            case ".avi" -> "video/x-msvideo";
            case ".mp3" -> "audio/mpeg";
            case ".wav" -> "audio/wav";
            case ".flac" -> "audio/flac";
            case ".aac" -> "audio/aac";
            case ".m4a" -> "audio/mp4";
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".zip" -> "application/zip";
            case ".rar" -> "application/x-rar-compressed";
            case ".7z" -> "application/x-7z-compressed";
            default -> "application/octet-stream";
        };
    }

    public Map<String, Object> getStorageInfo(Long ownerId) {
        List<FileEntity> files = fileRepo.findByOwnerIdAndIsTrashOrderByUpdatedAtDesc(ownerId, 0);
        long used = files.stream().mapToLong(FileEntity::getSize).sum();
        return Map.of("used", used, "total", 10737418240L, "fileCount", files.size());
    }
}
