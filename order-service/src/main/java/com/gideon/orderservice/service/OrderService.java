package com.gideon.orderservice.service;

import com.gideon.orderservice.dto.InventoryResponse;
import com.gideon.orderservice.dto.OrderLineItemsDto;
import com.gideon.orderservice.dto.OrderRequest;
import com.gideon.orderservice.model.Order;
import com.gideon.orderservice.model.OrderLineItems;
import com.gideon.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public void placeOrder(@RequestBody OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        order.setOrderLineItemsList(
                orderRequest.getOrderLineItemsDtoList()
                    .stream()
                    .map(this::mapToDto)
                    .toList()
        );

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        log.info("Calling Inventory Service for SKUs: {}", skuCodes);

        // Call Inventory Service, and place order if product is in
        // stock
        InventoryResponse[] inventoryResponsesArray = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        if(inventoryResponsesArray == null) {
            throw new RuntimeException("Inventory service returned null response");
        }

        boolean isAllProductInStock = Arrays.stream(inventoryResponsesArray)
                .allMatch(InventoryResponse::getIsInStock);

        log.info("Saving Order to Database: {}", order);

        if(isAllProductInStock) {
            orderRepository.save(order);
        }
        else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Product is not in stock, please try again later");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .skuCode(orderLineItemsDto.getSkuCode())
                .price(orderLineItemsDto.getPrice())
                .quantity(orderLineItemsDto.getQuantity())
                .build();
    }
}
