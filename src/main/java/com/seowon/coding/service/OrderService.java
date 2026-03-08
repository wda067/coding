package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.Order.OrderStatus;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }

    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // TODO #3: 구현 항목
        // * 주어진 고객 정보로 새 Order를 생성
        // * 지정된 Product를 주문에 추가
        // * order 의 상태를 PENDING 으로 변경
        // * orderDate 를 현재시간으로 설정
        // * order 를 저장
        // * 각 Product 의 재고를 수정
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.

        Order order = Order.createOrder(customerName, customerEmail);
        orderRepository.save(order);

        for (int i = 0; i < productIds.size(); i++) {
            Product product = productRepository.findById(productIds.get(i))
                    .orElseThrow();
            Integer quantity = quantities.get(i);

            order.addProduct(product, quantity);
        }

        return order;
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존을 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.createOrder(customerName, customerEmail);

        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));

            order.addProduct(product, qty);
        }

        order.checkout(couponCode);

        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업을 간주하여 진행률 저장을 위한 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리(장시간 작업이라고 가정함) 중 진행률을 저장하여 다른 사용자가 변화하는 진행률을 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     *
     * 1. bulkShipOrdersParent()에 전체 트랜잭션이 걸려있어
     * 반복해서 장시간 작업을 하면서 장시간 DB 커넥션을 점유하게 됩니다.
     * 따라서 부모 메서드에 걸린 트랜잭션을 제거하고 DB 작업은 짧은 트랜잭션으로 처리해야 합니다.
     *
     * 2. 중간 진행률을 저장하는 updateProgressRequiresNew() 메서드는
     * 현재 동일한 클래스에 존재하는 메서드로 스프링에서 @Transactional는 프록시 기반으로 동작하여
     * 트랜잭션이 정상적으로 동작하지 않게 됩니다.
     * 따라서 외부 클래스에 메서드를 분리시킬 필요가 있습니다.
     *
     * 3. 장시간 작업을 진행하므로 bulkShipOrdersParent() 메서드는 비동기로 실행시켜
     * 사용자에게 빠른 응답을 내려줄 수 있습니다.
     *
     * 4. 현재 try-catch를 통해 예외가 발생하여도 계속해서 반복문이 진행되도록 되어있는데
     * 장시간 작업이 실패할 경우 어떻게 처리할 것인지 정의를 해야할 거 같습니다.
     * 또한 마지막에 markCompleted()로 완료 처리가 돼어 중간에 실패한 것에 대해 사용자가 알 수가 없게 됩니다.
     * 일단 실패했을 때 processed와 함께 로그를 남겨둘 수 있을 거 같습니다.
     *
     * 5. 장시간 작업과 현재 setStatus()가 하나의 작업으로 처리되는데
     * 장시간 작업은 DB 트랜잭션과 분리시키고 setStatus()는 별도의 트랜잭션으로 처리하고 실패 시 롤백 처리가 필요합니다.
     *
     * 6. 현재 메서드 앞뒤로 save()가 반복적으로 선언되어 있는데
     * JPA에서는 더티체킹으로 엔티티의 변경사항을 save()없이도 반영시켜 줍니다.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}