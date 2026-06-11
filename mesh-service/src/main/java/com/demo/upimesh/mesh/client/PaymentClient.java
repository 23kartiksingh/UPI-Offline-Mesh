package com.demo.upimesh.mesh.client;

import com.demo.upimesh.mesh.model.SettlementRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/internal/settle")
    Map<String, Object> settlePayment(
            @RequestBody SettlementRequest request,
            @RequestHeader("X-Bridge-Node-Id") String bridgeNodeId,
            @RequestHeader("X-Hop-Count") int hopCount
    );
}
