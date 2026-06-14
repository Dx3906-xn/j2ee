package com.docmgr.service;

import com.docmgr.entity.*;
import com.docmgr.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TeamService {

    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;
    private final UserRepository userRepo;

    public TeamService(TeamRepository teamRepo, TeamMemberRepository memberRepo, UserRepository userRepo) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
    }

    public List<Team> listTeams(Long userId) {
        return teamRepo.findByUserId(userId);
    }

    @Transactional
    public Team createTeam(String name, String description, Long ownerId) {
        Team team = teamRepo.save(new Team(name, description, ownerId));
        memberRepo.save(new TeamMember(team.getId(), ownerId, "owner"));
        return team;
    }

    @Transactional
    public void deleteTeam(Long id, Long ownerId) {
        Team team = teamRepo.findById(id).orElseThrow(() -> new RuntimeException("团队不存在"));
        if (!team.getOwnerId().equals(ownerId)) throw new RuntimeException("无权操作");
        teamRepo.deleteById(id);
    }

    public List<Map<String, Object>> getMembers(Long teamId) {
        List<TeamMember> members = memberRepo.findByTeamId(teamId);
        return members.stream().map(tm -> {
            User user = userRepo.findById(tm.getUserId()).orElse(null);
            if (user == null) return null;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("email", user.getEmail() != null ? user.getEmail() : "");
            map.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            map.put("role", tm.getRole());
            map.put("joined_at", tm.getJoinedAt().toString());
            return map;
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public void inviteMember(Long teamId, String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (memberRepo.findByTeamIdAndUserId(teamId, user.getId()).isPresent()) {
            throw new RuntimeException("该用户已是团队成员");
        }
        memberRepo.save(new TeamMember(teamId, user.getId(), "member"));
    }

    @Transactional
    public void removeMember(Long teamId, Long userId) {
        memberRepo.deleteByTeamIdAndUserIdAndRoleNot(teamId, userId, "owner");
    }
}