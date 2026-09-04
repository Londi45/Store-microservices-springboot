package com.gilbert.productservice.service;

import com.gilbert.productservice.dto.InventoryRequest;
import com.gilbert.productservice.dto.ProductRequest;
import com.gilbert.productservice.dto.ProductResponse;
import com.gilbert.productservice.model.Product;
import com.gilbert.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final WebClient.Builder webClientBuilder;

    public void createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();
        productRepository.save(product);
        log.info("Product {} is saved.", product.getId());

        // Create inventory record with quantity 0 for the new product
        String skuCode = product.getId();
        InventoryRequest inventoryRequest = InventoryRequest.builder()
                .skuCode(skuCode)
                .quantity(0)
                .build();

        webClientBuilder.build()
                .post()
                .uri("http://inventory-service/api/inventory")
                .bodyValue(inventoryRequest)
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        log.info("Inventory record created for product {} with skuCode {}", product.getId(), skuCode);
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToProductResponse).toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
