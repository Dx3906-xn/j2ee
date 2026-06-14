package com.docmgr.repository;

import com.docmgr.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {
    Optional<Share> findByCode(String code);
}
