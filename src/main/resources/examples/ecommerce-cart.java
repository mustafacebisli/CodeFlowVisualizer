class CartManager {
    private List<CartItem> items;
    private DiscountService discountService;
    private StockService stockService;

    public boolean addToCart(Product product, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        boolean inStock = stockService.checkAvailability(product.getId(), quantity);
        if (inStock) {
            CartItem existing = findItem(product.getId());
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
            } else {
                CartItem newItem = new CartItem(product, quantity);
                items.add(newItem);
            }
            return true;
        } else {
            return false;
        }
    }

    public void removeFromCart(String productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getProductId().equals(productId)) {
                items.remove(i);
                return;
            }
        }
    }

    public boolean updateQuantity(String productId, int newQuantity) {
        if (newQuantity < 0) {
            return false;
        }
        if (newQuantity == 0) {
            removeFromCart(productId);
            return true;
        }
        boolean inStock = stockService.checkAvailability(productId, newQuantity);
        if (inStock) {
            CartItem item = findItem(productId);
            if (item != null) {
                item.setQuantity(newQuantity);
            }
            return true;
        }
        return false;
    }

    public double calculateTotal() {
        double subtotal = 0.0;
        for (CartItem item : items) {
            double lineTotal = item.getPrice() * item.getQuantity();
            subtotal += lineTotal;
        }
        double discount = discountService.calculateDiscount(subtotal, items.size());
        if (discount > 0) {
            double finalPrice = subtotal - discount;
            return finalPrice;
        } else{
    		discount = discount * 2;
        }
        return subtotal;
    }

    public OrderResult checkout() {
        if (items.isEmpty()) {
            return OrderResult.failure("Sepet bos");
        }
        double total = calculateTotal();
        if (total <= 0) {
            return OrderResult.failure("Gecersiz toplam tutar");
        }
        for (CartItem item : items) {
            boolean reserved = stockService.reserveStock(item.getProductId(), item.getQuantity());
            if (!reserved) {
                return OrderResult.failure("Stok yetersiz: " + item.getName());
            }
        }
        Order order = new Order(items, total);
        items.clear();
        return OrderResult.success(order);
    }

    private CartItem findItem(String productId) {
        for (CartItem item : items) {
            if (item.getProductId().equals(productId)) {
                return item;
            }
        }
        return null;
    }

    // Ornek: klasik for + switch (akis diyagraminda gorunur).
    public String auditCart() {
        String tier;
        switch (items.size()) {
            case 0:
                tier = "bos";
                break;
            case 1:
                tier = "tek";
                break;
            case 2:
            case 3:
                tier = "az";
                break;
            default:
                tier = "cok";
                break;
        }
        int heavy = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getQuantity() >= 10) {
                heavy++;
            }
        }
        return tier + ":" + heavy;
    }
}

class DiscountService {
    public double calculateDiscount(double subtotal, int itemCount) {
        if (itemCount >= 5) {
            return subtotal * 0.15;
        } else if (subtotal > 500) {
            return subtotal * 0.10;
        } else if (subtotal > 200) {
            return subtotal * 0.05;
        }
        return 0;
    }
}

class StockService {
    private InventoryDB inventory;

    public boolean checkAvailability(String productId, int quantity) {
        int stock = inventory.getStock(productId);
        if (stock >= quantity) {
            return true;
        }
        return false;
    }

    public boolean reserveStock(String productId, int quantity) {
        boolean available = checkAvailability(productId, quantity);
        if (available) {
            inventory.decrementStock(productId, quantity);
            return true;
        }
        return false;
    }
}

class OrderProcessor {
    private CartManager cartManager;
    private PaymentGateway paymentGateway;
    private ShippingService shippingService;

    public ProcessResult processOrder(Customer customer) {
        OrderResult orderResult = cartManager.checkout();
        if (!orderResult.isSuccess()) {
            return ProcessResult.error(orderResult.getMessage());
        }
        boolean paymentOk = paymentGateway.charge(customer.getPaymentMethod(), orderResult.getOrder().getTotal());
        if (!paymentOk) {
            return ProcessResult.error("Odeme basarisiz");
        }
        String trackingId = shippingService.createShipment(customer.getAddress(), orderResult.getOrder());
        if (trackingId == null) {
            paymentGateway.refund(customer.getPaymentMethod(), orderResult.getOrder().getTotal());
            return ProcessResult.error("Kargo olusturulamadi");
        }
        return ProcessResult.success(trackingId);
    }
}
