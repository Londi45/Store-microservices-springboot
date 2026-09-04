package com.gilbert.orderservice.service;

import com.gilbert.orderservice.dto.InventoryResponse;
import com.gilbert.orderservice.dto.OrderLineItemsDto;
import com.gilbert.orderservice.dto.OrderRequest;
import com.gilbert.orderservice.dto.OrderResponse;
import com.gilbert.orderservice.exception.OutOfStockException;
import com.gilbert.orderservice.model.Order;
import com.gilbert.orderservice.model.OrderLineItems;
import com.gilbert.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        List<OrderLineItems> lineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToEntity)
                .toList();

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setOrderLineItemsList(lineItems);

        List<String> skuCodes = lineItems.stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        log.info("Checking inventory for skuCodes: {}", skuCodes);

        InventoryResponse[] inventoryResponse = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uri -> uri.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        if (inventoryResponse == null || inventoryResponse.length == 0) {
            throw new OutOfStockException("Unable to verify stock. Please try again.");
        }

        List<String> outOfStock = Arrays.stream(inventoryResponse)
                .filter(i -> !i.isInStock())
                .map(InventoryResponse::getSkuCode)
                .toList();

        if (!outOfStock.isEmpty()) {
            throw new OutOfStockException("The following items are out of stock: " + outOfStock);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} placed successfully", savedOrder.getOrderNumber());

        return mapToResponse(savedOrder);
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto dto) {
        OrderLineItems item = new OrderLineItems();
        item.setSkuCode(dto.getSkuCode());
        item.setPrice(dto.getPrice());
        item.setQuantity(dto.getQuantity());
        return item;
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderLineItemsDto> itemDtos = order.getOrderLineItemsList().stream()
                .map(item -> new OrderLineItemsDto(
                        item.getId(),
                        item.getSkuCode(),
                        item.getPrice(),
                        item.getQuantity()))
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status("PLACED")
                .orderLineItems(itemDtos)
                .build();
    }
}
