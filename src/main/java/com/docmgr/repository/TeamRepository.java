package com.docmgr.repository;

import com.docmgr.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN TeamMember tm ON t.id = tm.teamId WHERE t.ownerId = :userId OR tm.userId = :userId")
    List<Team> findByUserId(Long userId);

    List<Team> findByOwnerIdAndNameContaining(Long ownerId, String name);
}
