package com.demo.upimesh.mesh.service;

import com.demo.upimesh.mesh.model.MeshPacket;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MeshSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        devices.put("phone-alice", new VirtualDevice("phone-alice", false));
        devices.put("phone-bob", new VirtualDevice("phone-bob", false));
        devices.put("phone-carol", new VirtualDevice("phone-carol", false));
        devices.put("bridge-dave", new VirtualDevice("bridge-dave", true));
        devices.put("bridge-eve", new VirtualDevice("bridge-eve", true));
        log.info("Initialized mesh simulator with 5 virtual devices (2 online bridges)");
    }

    public void injectPacket(String deviceId, MeshPacket packet) {
        VirtualDevice dev = devices.get(deviceId);
        if (dev != null) dev.hold(packet);
    }

    public int simulateGossipRound() {
        List<VirtualDevice> deviceList = new ArrayList<>(devices.values());
        int newTransfers = 0;

        for (int i = 0; i < deviceList.size(); i++) {
            VirtualDevice a = deviceList.get(i);
            VirtualDevice b = deviceList.get((i + 1) % deviceList.size());

            for (MeshPacket packet : a.getHeldPackets()) {
                if (!b.holds(packet.getPacketId())) {
                    b.hold(packet);
                    newTransfers++;
                }
            }
            for (MeshPacket packet : b.getHeldPackets()) {
                if (!a.holds(packet.getPacketId())) {
                    a.hold(packet);
                    newTransfers++;
                }
            }
        }
        return newTransfers;
    }

    public List<VirtualDevice> getAllDevices() {
        return new ArrayList<>(devices.values());
    }

    public List<VirtualDevice> getOnlineBridges() {
        return devices.values().stream().filter(VirtualDevice::hasInternet).toList();
    }

    public void clearAll() {
        devices.values().forEach(VirtualDevice::clear);
    }
}
