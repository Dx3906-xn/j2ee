package com.docmgr.repository;

import com.docmgr.entity.RecentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface RecentAccessRepository extends JpaRepository<RecentAccess, Long> {

    @Query("SELECT ra FROM RecentAccess ra JOIN FileEntity f ON ra.fileId = f.id WHERE ra.userId = :userId AND f.isTrash = 0 ORDER BY ra.accessedAt DESC")
    List<RecentAccess> findRecentWithFiles(Long userId);

    @Modifying @Transactional
    @Query(value = "INSERT INTO recent_access (file_id, user_id, accessed_at) VALUES (:fileId, :userId, NOW()) ON DUPLICATE KEY UPDATE accessed_at = NOW()", nativeQuery = true)
    void upsertAccess(Long fileId, Long userId);
}
