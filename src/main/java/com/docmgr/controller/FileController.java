package com.docmgr.controller;

import com.docmgr.entity.*;
import com.docmgr.service.FileService;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    private Long uid(Authentication auth) { return (Long) auth.getPrincipal(); }

    @GetMapping("/folders/list")
    public ResponseEntity<?> listFolders(@RequestParam(defaultValue = "0") Long parent_id, Authentication auth) {
        return ResponseEntity.ok(Map.of("folders", fileService.listFolders(uid(auth), parent_id)));
    }

    @PostMapping("/folders/create")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, Object> body, Authentication auth) {
        try {
            String name = (String) body.get("name");
            Long parentId = body.get("parent_id") != null ? Long.valueOf(body.get("parent_id").toString()) : 0L;
            Folder folder = fileService.createFolder(name, parentId, uid(auth));
            return ResponseEntity.status(201).body(Map.of("message", "创建成功",
                    "folder", Map.of("id", folder.getId(), "name", folder.getName(), "parent_id", folder.getParentId())));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long id, Authentication auth) {
        try { fileService.deleteFolder(id, uid(auth)); return ResponseEntity.ok(Map.of("message", "删除成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<?> renameFolder(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        try { fileService.renameFolder(id, body.get("name"), uid(auth)); return ResponseEntity.ok(Map.of("message", "重命名成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestParam(defaultValue = "0") Long folder_id,
                                       @RequestParam(defaultValue = "all") String filter,
                                       Authentication auth) {
        return ResponseEntity.ok(Map.of("files", fileService.listFiles(uid(auth), folder_id, filter)));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "0") Long folder_id,
                                     Authentication auth) {
        try {
            FileEntity f = fileService.uploadFile(file, folder_id, uid(auth));
            return ResponseEntity.status(201).body(Map.of("message", "上传成功",
                    "file", Map.of("id", f.getId(), "name", f.getName(), "folder_id", f.getFolderId(), "size", f.getSize(), "mime_type", f.getMimeType())));
        } catch (RuntimeException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable Long id, Authentication auth) {
        try {
            FileEntity file = fileService.getFile(id, uid(auth));
            Path path = Paths.get(file.getPath());
            if (!Files.exists(path)) return ResponseEntity.notFound().build();
            fileService.recordAccess(id, uid(auth));
            Resource resource = new UrlResource(path.toUri());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"").body(resource);
        } catch (RuntimeException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, Authentication auth) {
        try { fileService.deleteFile(id, uid(auth)); return ResponseEntity.ok(Map.of("message", "已移入回收站")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/permanent/{id}")
    public ResponseEntity<?> permanentDelete(@PathVariable Long id, Authentication auth) {
        try { fileService.permanentDelete(id, uid(auth)); return ResponseEntity.ok(Map.of("message", "永久删除成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/restore/{id}")
    public ResponseEntity<?> restore(@PathVariable Long id, Authentication auth) {
        try { fileService.restoreFile(id, uid(auth)); return ResponseEntity.ok(Map.of("message", "恢复成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/trash")
    public ResponseEntity<?> listTrash(Authentication auth) {
        return ResponseEntity.ok(Map.of("files", fileService.listTrash(uid(auth))));
    }

    @DeleteMapping("/trash/empty")
    public ResponseEntity<?> emptyTrash(Authentication auth) {
        fileService.emptyTrash(uid(auth));
        return ResponseEntity.ok(Map.of("message", "回收站已清空"));
    }

    @PutMapping("/rename/{id}")
    public ResponseEntity<?> rename(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        try { fileService.renameFile(id, body.get("name"), uid(auth)); return ResponseEntity.ok(Map.of("message", "重命名成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/move/{id}")
    public ResponseEntity<?> move(@PathVariable Long id, @RequestBody Map<String, Long> body, Authentication auth) {
        try { fileService.moveFile(id, body.getOrDefault("folder_id", 0L), uid(auth)); return ResponseEntity.ok(Map.of("message", "移动成功")); }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/copy/{id}")
    public ResponseEntity<?> copy(@PathVariable Long id, @RequestBody Map<String, Long> body, Authentication auth) {
        try {
            FileEntity copy = fileService.copyFile(id, body.getOrDefault("folder_id", 0L), uid(auth));
            return ResponseEntity.ok(Map.of("message", "复制成功", "file", Map.of("id", copy.getId(), "name", copy.getName())));
        } catch (RuntimeException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/favorite/{id}")
    public ResponseEntity<?> toggleFav(@PathVariable Long id, Authentication auth) {
        fileService.toggleFavorite(id, uid(auth));
        return ResponseEntity.ok(Map.of("message", "操作成功"));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> listFavorites(Authentication auth) {
        return ResponseEntity.ok(Map.of("files", fileService.listFavorites(uid(auth))));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> listRecent(Authentication auth) {
        return ResponseEntity.ok(Map.of("files", fileService.listRecent(uid(auth))));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<?> preview(@PathVariable Long id, Authentication auth) {
        try { return ResponseEntity.ok(fileService.previewFile(id, uid(auth))); }
        catch (RuntimeException | IOException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<?> stream(@PathVariable Long id) {
        try {
            FileEntity file = fileService.getFilePublic(id);
            Path path = Paths.get(file.getPath());
            if (!Files.exists(path)) return ResponseEntity.notFound().build();
            Resource resource = new UrlResource(path.toUri());
            String mime = file.getMimeType() != null ? file.getMimeType() : "application/octet-stream";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(resource);
        } catch (RuntimeException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q, Authentication auth) {
        return ResponseEntity.ok(fileService.search(q, uid(auth)));
    }

    @PostMapping("/share")
    public ResponseEntity<?> share(@RequestBody Map<String, Object> body, Authentication auth) {
        try {
            Long fileId = Long.valueOf(body.get("file_id").toString());
            Share share = fileService.createShare(fileId, (String) body.get("expiry"), (String) body.get("password"));
            return ResponseEntity.ok(Map.of("message", "分享链接创建成功", "code", share.getCode(), "url", "/share/" + share.getCode()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/share/{code}")
    public ResponseEntity<?> getShareInfo(@PathVariable String code) {
        try {
            Map<String, Object> info = fileService.getShareByCode(code);
            return ResponseEntity.ok(info);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/share/{code}/verify")
    public ResponseEntity<?> verifyShare(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        try {
            String password = body != null ? (String) body.get("password") : null;
            Map<String, Object> result = fileService.verifyShare(code, password);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/batch-delete")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, Object> body, Authentication auth) {
        List<?> raw = (List<?>) body.get("ids");
        if (raw == null) return ResponseEntity.badRequest().body(Map.of("message", "ids is required"));
        List<Long> ids = raw.stream().map(id -> Long.valueOf(id.toString())).toList();
        fileService.batchDelete(ids, uid(auth));
        return ResponseEntity.ok(Map.of("message", "已批量移入回收站"));
    }

    @GetMapping("/storage")
    public ResponseEntity<?> storage(Authentication auth) {
        return ResponseEntity.ok(fileService.getStorageInfo(uid(auth)));
    }
}