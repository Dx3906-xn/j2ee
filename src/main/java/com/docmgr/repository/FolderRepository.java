package com.docmgr.repository;

import com.docmgr.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerIdAndParentIdOrderByNameAsc(Long ownerId, Long parentId);
    List<Folder> findByOwnerIdAndNameContainingOrderByNameAsc(Long ownerId, String name);
    void deleteByOwnerIdAndId(Long ownerId, Long id);
}
