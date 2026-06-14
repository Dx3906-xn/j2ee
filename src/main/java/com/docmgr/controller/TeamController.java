package com.docmgr.controller;

import com.docmgr.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    private Long uid(Authentication auth) { return (Long) auth.getPrincipal(); }

    @GetMapping("/list")
    public ResponseEntity<?> list(Authentication auth) {
        return ResponseEntity.ok(Map.of("teams", teamService.listTeams(uid(auth))));
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        try {
            var team = teamService.createTeam(body.get("name"), body.get("description"), uid(auth));
            return ResponseEntity.status(201).body(Map.of("message", "创建成功",
                    "team", Map.of("id", team.getId(), "name", team.getName(), "description",
                            team.getDescription() != null ? team.getDescription() : "")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        try {
            teamService.deleteTeam(id, uid(auth));
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> members(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("members", teamService.getMembers(id)));
    }

    @PostMapping("/invite")
    public ResponseEntity<?> invite(@RequestBody Map<String, Object> body) {
        try {
            Long teamId = Long.valueOf(body.get("team_id").toString());
            String username = (String) body.get("username");
            teamService.inviteMember(teamId, username);
            return ResponseEntity.status(201).body(Map.of("message", "邀请成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/removeMember")
    public ResponseEntity<?> removeMember(@RequestBody Map<String, Long> body) {
        try {
            teamService.removeMember(body.get("team_id"), body.get("user_id"));
            return ResponseEntity.ok(Map.of("message", "移除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
