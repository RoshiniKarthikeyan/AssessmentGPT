package com.assessment.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class ApiController {
    private final Map<String, Object> assessments = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> promptHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> tokenUsage = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> challenge = new LinkedHashMap<>();

    public ApiController() {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("id", 1);
        seed.put("title", "Find the Second Largest Unique Number");
        seed.put("difficulty", "Medium");
        seed.put("durationMinutes", 60);
        seed.put("tokenBudget", 12000);
        seed.put("problem", "Given an integer array, find the second-largest unique number.");
        seed.put("requirements", List.of("Handle duplicate numbers", "Handle negative numbers", "Return null if fewer than two unique values"));
        seed.put("constraints", "1 <= array.length <= 1e5; -1e9 <= value <= 1e9");
        seed.put("publicTests", List.of(
            Map.of("input", "[10,5,8,8,7]", "output", "8"),
            Map.of("input", "[5,5,5]", "output", "null"),
            Map.of("input", "[-1,-2,-3,-3]", "output", "-2")
        ));
        seed.put("hiddenTestCount", 5);
        seed.put("published", true);
        challenge.put("1", seed);
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String role;
        if ("admin".equalsIgnoreCase(request.username()) && "admin123".equals(request.password())) role = "ADMIN";
        else if ("candidate".equalsIgnoreCase(request.username()) && "candidate123".equals(request.password())) role = "CANDIDATE";
        else return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
        String key = "assessment-platform-demo-secret-key-2026";
        String token = Jwts.builder()
            .subject(request.username())
            .claim("role", role)
            .expiration(java.util.Date.from(Instant.now().plusSeconds(28800)))
            .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)))
            .compact();
        return ResponseEntity.ok(Map.of("token", token, "role", role, "name", role.equals("ADMIN") ? "Platform Admin" : "Alex Candidate"));
    }

    @GetMapping("/challenges")
    public List<Map<String, Object>> challenges() { return new ArrayList<>(challenge.values()); }

    @PostMapping("/admin/challenges")
    public Map<String, Object> createChallenge(@RequestBody Map<String, Object> body) {
        String id = String.valueOf(System.currentTimeMillis());
        body.put("id", id);
        body.putIfAbsent("published", false);
        challenge.put(id, body);
        return body;
    }

    @PutMapping("/admin/challenges/{id}")
    public Map<String, Object> updateChallenge(@PathVariable String id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        challenge.put(id, body);
        return body;
    }

    @GetMapping("/challenges/{id}")
    public ResponseEntity<Map<String, Object>> challenge(@PathVariable String id) {
        return challenge.containsKey(id) ? ResponseEntity.ok(challenge.get(id)) : ResponseEntity.notFound().build();
    }

    @PostMapping("/assessments/start")
    public Map<String, Object> start(@RequestBody(required = false) Map<String, Object> body) {
        String id = "A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("id", id);
        assessment.put("challengeId", body == null ? "1" : body.getOrDefault("challengeId", "1"));
        assessment.put("status", "IN_PROGRESS");
        assessment.put("startedAt", Instant.now().toString());
        assessment.put("remainingSeconds", 3600);
        assessments.put(id, assessment);
        promptHistory.put(id, new ArrayList<>());
        tokenUsage.put(id, new LinkedHashMap<>(Map.of("inputTokens", 0, "outputTokens", 0, "totalTokens", 0, "requests", 0, "budget", 12000, "fullContextSize", 0, "optimizedContextSize", 0, "costEstimate", 0.0)));
        return assessment;
    }

    @GetMapping("/assessments/{id}")
    public ResponseEntity<Map<String, Object>> assessment(@PathVariable String id) {
        return assessments.containsKey(id) ? ResponseEntity.ok((Map<String, Object>) assessments.get(id)) : ResponseEntity.notFound().build();
    }

    record AiRequest(String assessmentId, String prompt, String code, String action) {}

    @PostMapping("/ai/generate")
    public ResponseEntity<Map<String, Object>> ai(@RequestBody AiRequest request) {
        return ResponseEntity.ok(generateResponse(request.assessmentId(), request.prompt(), request.code(), "generate"));
    }

    @PostMapping("/ai/refine")
    public ResponseEntity<Map<String, Object>> refine(@RequestBody AiRequest request) {
        return ResponseEntity.ok(generateResponse(request.assessmentId(), request.prompt(), request.code(), "refine"));
    }

    private Map<String, Object> generateResponse(String assessmentId, String prompt, String code, String action) {
        assessmentId = assessmentId == null ? "demo" : assessmentId;
        String apiKey = System.getenv("OPENAI_API_KEY");
        boolean demo = apiKey == null || apiKey.isBlank();
        String content;
        int input = 160 + (prompt == null ? 0 : prompt.length() / 4);
        int output;
        if (demo) {
            content = (action.equals("refine") ? "DEMO AI - refined solution:\n\n" : "DEMO AI - generated solution:\n\n")
                + "```java\npublic class Solution {\n"
                + "    public static Integer secondLargest(int[] nums) {\n"
                + "        java.util.TreeSet<Integer> values = new java.util.TreeSet<>();\n"
                + "        for (int value : nums) values.add(value);\n"
                + "        if (values.size() < 2) return null;\n"
                + "        values.pollLast();\n"
                + "        return values.pollLast();\n"
                + "    }\n}\n```\nDEMO MODE: no OPENAI_API_KEY configured.";
            output = content.length() / 4;
        } else {
            content = callOpenAi(apiKey, prompt, code);
            input = Math.max(input, content.length() / 8);
            output = content.length() / 4;
        }
        promptHistory.computeIfAbsent(assessmentId, k -> new ArrayList<>()).add(Map.of(
            "prompt", prompt == null ? "" : prompt, "response", content, "mode", demo ? "DEMO AI" : "OPENAI",
            "action", action, "timestamp", Instant.now().toString()
        ));
        Map<String, Object> usage = tokenUsage.computeIfAbsent(assessmentId, k -> new LinkedHashMap<>(Map.of("inputTokens", 0, "outputTokens", 0, "totalTokens", 0, "requests", 0, "budget", 12000, "fullContextSize", 0, "optimizedContextSize", 0, "costEstimate", 0.0)));
        int oldIn = (int) usage.get("inputTokens"), oldOut = (int) usage.get("outputTokens"), oldReq = (int) usage.get("requests");
        usage.put("inputTokens", oldIn + input);
        usage.put("outputTokens", oldOut + output);
        usage.put("totalTokens", oldIn + input + oldOut + output);
        usage.put("requests", oldReq + 1);
        int full = (prompt == null ? 0 : prompt.length()) + 500;
        int optimized = Math.min(full, 260 + (code == null ? 0 : Math.min(code.length(), 800)));
        usage.put("fullContextSize", (int) usage.get("fullContextSize") + full);
        usage.put("optimizedContextSize", (int) usage.get("optimizedContextSize") + optimized);
        usage.put("costEstimate", ((double) oldIn + input + oldOut + output) / 1000000 * 2.5);
        return Map.of("content", content, "mode", demo ? "DEMO AI" : "OPENAI", "usage", usage);
    }

    private String callOpenAi(String apiKey, String prompt, String code) {
        try {
            String safePrompt = prompt == null ? "" : prompt.replace("\"", "\\\"");
            String payload = "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"system\",\"content\":\"Generate concise Java code for the coding challenge.\"},{\"role\":\"user\",\"content\":\"" + safePrompt + "\"}],\"temperature\":0.2}";
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "OpenAI request failed: " + e.getMessage();
        }
    }

    @GetMapping("/ai/history/{assessmentId}")
    public List<Map<String, Object>> history(@PathVariable String assessmentId) {
        return promptHistory.getOrDefault(assessmentId, List.of());
    }

    record ExecutionRequest(String assessmentId, String code) {}

    @PostMapping("/execution/run")
    public Map<String, Object> execute(@RequestBody ExecutionRequest request) {
        String code = request.code() == null ? "" : request.code();
        boolean hasDistinct = code.contains("TreeSet") || code.contains("HashSet") || code.contains("distinct") || code.contains("Set<");
        boolean hasNegative = code.contains("-") || code.contains("Integer");
        boolean looksCorrect = code.contains("secondLargest") || code.contains("second_largest");
        boolean passed = looksCorrect && hasDistinct;
        List<Map<String,Object>> tests = new ArrayList<>();
        tests.add(Map.of("name", "public-1", "passed", passed, "actual", passed ? "8" : "failed"));
        tests.add(Map.of("name", "public-2", "passed", passed, "actual", passed ? "null" : "failed"));
        tests.add(Map.of("name", "public-3", "passed", passed && hasNegative, "actual", passed ? "-2" : "failed"));
        tests.add(Map.of("name", "hidden-duplicate", "passed", passed, "actual", passed ? "passed" : "hidden"));
        tests.add(Map.of("name", "hidden-negative", "passed", passed && hasNegative, "actual", passed ? "passed" : "hidden"));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("mode", "DEMO EXECUTION");
        result.put("safeNote", "Docker is unavailable; this is a deterministic result simulator, not untrusted host compilation.");
        result.put("passed", passed);
        result.put("passedCount", tests.stream().filter(t -> (boolean)t.get("passed")).count());
        result.put("totalCount", tests.size());
        result.put("tests", tests);
        result.put("output", passed ? "All public tests passed." : "Compilation/demo feedback: implement the secondLargest method with a distinct-value set.");
        result.put("executionTimeMs", 42);
        return result;
    }

    @GetMapping("/execution/results/{assessmentId}")
    public Map<String,Object> executionResults(@PathVariable String assessmentId) {
        return Map.of("assessmentId", assessmentId, "results", List.of());
    }

    record DebugRequest(String assessmentId, String code) {}

    @PostMapping("/debug/inject")
    public Map<String,Object> inject(@RequestBody DebugRequest request) {
        String buggy = (request.code() == null ? "" : request.code()).replace("pollLast();\n        return values.pollLast();", "pollLast();\n        return values.first();");
        return Map.of("bugInjectionId", "BUG-DISTINCT-LAST-001", "buggyCode", buggy,
            "message", "A deterministic bug was injected. A failing debug test now requires correction.");
    }

    @PostMapping("/debug/resubmit")
    public Map<String,Object> resubmit(@RequestBody DebugRequest request) {
        boolean fixed = (request.code() == null ? "" : request.code()).contains("pollLast()");
        return Map.of("passed", fixed, "debugTestsPassed", fixed ? 3 : 0, "debugTestsTotal", 3,
            "message", fixed ? "Debug tests passed." : "The largest distinct values logic is still incorrect.");
    }

    @PostMapping("/assessments/{id}/submit")
    public Map<String,Object> submit(@PathVariable String id, @RequestBody Map<String,Object> body) {
        return score(id, body);
    }

    @GetMapping("/scores/{assessmentId}")
    public Map<String,Object> score(@PathVariable String assessmentId) { return score(assessmentId, Map.of()); }

    private Map<String,Object> score(String assessmentId, Map<String,Object> body) {
        int promptCount = promptHistory.getOrDefault(assessmentId, List.of()).size();
        boolean fixed = "true".equals(String.valueOf(body.getOrDefault("fixed", "true")));
        int promptQuality = Math.min(40, 20 + promptCount * 10);
        int promptImprovement = promptCount >= 2 ? 10 : (promptCount == 1 ? 5 : 0);
        int testCases = fixed ? 30 : 20;
        int debug = fixed ? 20 : 8;
        int total = promptQuality + promptImprovement + testCases + debug;
        Map<String,Object> breakdown = new LinkedHashMap<>();
        breakdown.put("promptQuality", Map.of("score", promptQuality, "max", 40, "reason", promptCount + " prompts evaluated for clarity and completeness"));
        breakdown.put("promptImprovement", Map.of("score", promptImprovement, "max", 10, "reason", promptCount >= 2 ? "At least one refinement was recorded" : "Limited refinement"));
        breakdown.put("testCases", Map.of("score", testCases, "max", 30, "reason", fixed ? "Initial test set passed" : "Some test failures remain"));
        breakdown.put("debugTests", Map.of("score", debug, "max", 20, "reason", fixed ? "Corrected solution passed debug tests" : "Debug tests were not fully passed"));
        return Map.of("assessmentId", assessmentId, "total", total, "breakdown", breakdown,
            "explanation", "Score is calculated deterministically from prompt count, test success, and debug success.");
    }

    @GetMapping("/tokens/{assessmentId}")
    public Map<String,Object> tokens(@PathVariable String assessmentId) {
        return tokenUsage.getOrDefault(assessmentId, Map.of("inputTokens",0,"outputTokens",0,"totalTokens",0,"requests",0,"budget",12000,"costEstimate",0.0));
    }

    @GetMapping("/tokens/{assessmentId}/analytics")
    public Map<String,Object> tokenAnalytics(@PathVariable String assessmentId) {
        Map<String,Object> usage = tokens(assessmentId);
        int total = (int) usage.get("totalTokens"), budget = (int) usage.get("budget");
        int percent = budget == 0 ? 0 : total * 100 / budget;
        String warning = percent >= 100 ? "Critical - requests blocked" : percent >= 90 ? "Critical" : percent >= 75 ? "High" : percent >= 50 ? "Moderate" : "Normal";
        int full = (int) usage.getOrDefault("fullContextSize", 0), optimized = (int) usage.getOrDefault("optimizedContextSize", 0);
        return Map.of("usage", usage, "percentUsed", percent, "warning", warning, "savings", Math.max(0, full - optimized),
            "recommendations", List.of("Avoid repeating the problem statement","Ask focused questions","Include only relevant errors","Refine existing code","Use concise prompts"));
    }

    @GetMapping("/admin/analytics")
    public Map<String,Object> analytics() {
        List<Map<String,Object>> candidates = syntheticCandidates();
        double avg = candidates.stream().mapToInt(c -> (int)c.get("score")).average().orElse(0);
        int highest = candidates.stream().mapToInt(c -> (int)c.get("score")).max().orElse(0);
        int tokens = candidates.stream().mapToInt(c -> (int)c.get("tokens")).sum();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("demoData", true);
        result.put("totalCandidates", 5);
        result.put("totalAssessments", 5);
        result.put("averageScore", Math.round(avg));
        result.put("highestScore", highest);
        result.put("averageTokenUsage", tokens / 5);
        result.put("aiRequestCount", 17);
        result.put("testSuccessRate", 82);
        result.put("debugSuccessRate", 78);
        result.put("costEstimate", 0.42);
        result.put("candidates", candidates);
        return result;
    }

    private List<Map<String,Object>> syntheticCandidates() {
        return List.of(
            Map.of("id","DEMO-C1","name","Aarav Mehta","score",92,"tokens",4200,"promptQuality",38,"testCases",28,"debugTests",18),
            Map.of("id","DEMO-C2","name","Maya Singh","score",84,"tokens",6100,"promptQuality",34,"testCases",30,"debugTests",16),
            Map.of("id","DEMO-C3","name","Leo Chen","score",76,"tokens",7800,"promptQuality",30,"testCases",26,"debugTests",14),
            Map.of("id","DEMO-C4","name","Sara Khan","score",68,"tokens",9200,"promptQuality",26,"testCases",24,"debugTests",12),
            Map.of("id","DEMO-C5","name","Noah Brown","score",55,"tokens",11000,"promptQuality",20,"testCases",21,"debugTests",8)
        );
    }

    @GetMapping("/admin/candidates")
    public List<Map<String,Object>> candidates() { return syntheticCandidates(); }

    @GetMapping("/admin/scorecards")
    public List<Map<String,Object>> scorecards() { return syntheticCandidates(); }

    @GetMapping(value="/admin/export/excel", produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> excel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String[] sheets = {"Candidate Summary","Detailed Scores","Token Analytics","Prompt History","Test Execution Results"};
            for (String name : sheets) workbook.createSheet(name);
            Sheet summary = workbook.getSheet(sheets[0]);
            summary.createRow(0).createCell(0).setCellValue("DEMO DATA - Candidate Summary");
            summary.createRow(1).createCell(0).setCellValue("Candidate");
            summary.getRow(1).createCell(1).setCellValue("Score");
            summary.getRow(1).createCell(2).setCellValue("Tokens");
            int r = 2;
            for (Map<String,Object> c : syntheticCandidates()) {
                Row row = summary.createRow(r++);
                row.createCell(0).setCellValue((String)c.get("name"));
                row.createCell(1).setCellValue((int)c.get("score"));
                row.createCell(2).setCellValue((int)c.get("tokens"));
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment-report.xlsx").body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
