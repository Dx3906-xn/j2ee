package com.docmgr.repository;

import com.docmgr.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    List<FileEntity> findByOwnerIdAndIsTrashAndFolderIdOrderByUpdatedAtDesc(Long ownerId, Integer isTrash, Long folderId);

    List<FileEntity> findByOwnerIdAndIsTrashAndMimeTypeContainingIgnoreCaseOrderByUpdatedAtDesc(Long ownerId, Integer isTrash, String mimeType);

    List<FileEntity> findByOwnerIdAndIsTrashAndFolderIdAndMimeTypeContainingIgnoreCaseOrderByUpdatedAtDesc(Long ownerId, Integer isTrash, Long folderId, String mimeType);

    List<FileEntity> findByOwnerIdAndIsTrashOrderByUpdatedAtDesc(Long ownerId, Integer isTrash);

    List<FileEntity> findByOwnerIdAndIsFavoriteAndIsTrashOrderByUpdatedAtDesc(Long ownerId, Integer isFavorite, Integer isTrash);

    List<FileEntity> findByOwnerIdAndIsTrashAndNameContainingOrderByUpdatedAtDesc(Long ownerId, Integer isTrash, String name);

    @Modifying @Transactional
    @Query("UPDATE FileEntity f SET f.isFavorite = CASE WHEN f.isFavorite = 1 THEN 0 ELSE 1 END WHERE f.id = :id AND f.ownerId = :ownerId")
    void toggleFavorite(Long id, Long ownerId);

    @Modifying @Transactional
    @Query("UPDATE FileEntity f SET f.folderId = :folderId, f.updatedAt = CURRENT_TIMESTAMP WHERE f.id = :id AND f.ownerId = :ownerId")
    void moveToFolder(Long id, Long folderId, Long ownerId);
}
