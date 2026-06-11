package com.demo.upimesh.mesh.controller;

import com.demo.upimesh.mesh.model.MeshPacket;
import com.demo.upimesh.mesh.service.DemoService;
import com.demo.upimesh.mesh.service.MeshSimulatorService;
import com.demo.upimesh.mesh.service.VirtualDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MeshSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorController.class);

    @Autowired private MeshSimulatorService meshSimulator;
    @Autowired private DemoService demoService;
    @Autowired private StringRedisTemplate redisTemplate;

    @Value("${server.port}")
    private String serverPort;

    private final WebClient webClient = WebClient.create();

    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(@RequestBody Map<String, Object> req) {
        try {
            String sender = (String) req.getOrDefault("senderVpa", "alice@okaxis");
            String receiver = (String) req.getOrDefault("receiverVpa", "bob@okaxis");
            BigDecimal amount = new BigDecimal(req.get("amount").toString());
            String pin = (String) req.getOrDefault("pin", "1234");
            int ttl = req.containsKey("ttl") ? (int) req.get("ttl") : 5;
            String startDevice = (String) req.getOrDefault("startDevice", "phone-alice");

            MeshPacket packet = demoService.createPacket(sender, receiver, amount, pin, ttl);
            meshSimulator.injectPacket(startDevice, packet);

            return ResponseEntity.ok(Map.of(
                    "packetId", packet.getPacketId(),
                    "injectedAt", new Date(packet.getCreatedAt()).toString(),
                    "ttl", ttl,
                    "ciphertextPreview", packet.getCiphertext().substring(0, 32) + "..."
            ));
        } catch (Exception e) {
            log.error("Packet creation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Returns transfers count + per-device *delta* (packets gained this round) for the
    // frontend flash animation — the UI only lights up devices that actually received something.
    @PostMapping("/mesh/gossip")
    public ResponseEntity<?> gossip() {
        // Snapshot counts before gossiping
        Map<String, Integer> before = new HashMap<>();
        for (VirtualDevice d : meshSimulator.getAllDevices()) before.put(d.getDeviceId(), d.packetCount());

        int transfers = meshSimulator.simulateGossipRound();

        // Compute delta per device
        Map<String, Integer> delta = new HashMap<>();
        for (VirtualDevice d : meshSimulator.getAllDevices()) {
            int gain = d.packetCount() - before.getOrDefault(d.getDeviceId(), 0);
            delta.put(d.getDeviceId(), gain);
        }

        return ResponseEntity.ok(Map.of(
                "transfers", transfers,
                "deviceCounts", delta  // "counts" here = packets gained, used for flash animation
        ));
    }

    @PostMapping("/mesh/flush")
    public ResponseEntity<?> flushBridges() {
        List<VirtualDevice> bridges = meshSimulator.getOnlineBridges();
        List<Map<String, Object>> results = new ArrayList<>();
        int attempts = 0;

        for (VirtualDevice bridge : bridges) {
            for (MeshPacket packet : bridge.getHeldPackets()) {
                attempts++;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = webClient.post()
                            .uri("http://localhost:" + serverPort + "/api/bridge/ingest")
                            .header("X-Bridge-Node-Id", bridge.getDeviceId())
                            .header("X-Hop-Count", "3")
                            .bodyValue(packet)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    results.add(Map.of(
                            "bridgeNode", bridge.getDeviceId(),
                            "packetId", packet.getPacketId(),
                            "outcome", response.get("outcome"),
                            "reason", response.getOrDefault("reason", "")
                    ));
                } catch (Exception e) {
                    results.add(Map.of(
                            "bridgeNode", bridge.getDeviceId(),
                            "packetId", packet.getPacketId(),
                            "outcome", "ERROR",
                            "reason", e.getMessage() != null ? e.getMessage() : "unknown"
                    ));
                }
            }
            bridge.clear();
        }
        // Wipe the entire mesh network's memory (simulating a global Reverse-ACK)
        meshSimulator.clearAll();
        return ResponseEntity.ok(Map.of("uploadsAttempted", attempts, "results", results));
    }

    @PostMapping("/mesh/reset")
    public ResponseEntity<?> reset() {
        meshSimulator.clearAll();
        Set<String> keys = redisTemplate.keys("idempotency:mesh:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    @GetMapping("/mesh/state")
    public ResponseEntity<?> state() {
        List<Map<String, Object>> deviceStates = meshSimulator.getAllDevices().stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", d.getDeviceId());
            m.put("hasInternet", d.hasInternet());
            m.put("packetCount", d.packetCount());
            m.put("packetIds", d.getHeldPackets().stream().map(MeshPacket::getPacketId).toList());
            return m;
        }).collect(Collectors.toList());

        Set<String> keys = redisTemplate.keys("idempotency:mesh:*");
        int cacheSize = keys != null ? keys.size() : 0;

        return ResponseEntity.ok(Map.of("devices", deviceStates, "idempotencyCacheSize", cacheSize));
    }
}
