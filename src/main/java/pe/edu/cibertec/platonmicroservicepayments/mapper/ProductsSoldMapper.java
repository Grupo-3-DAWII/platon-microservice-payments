package pe.edu.cibertec.platonmicroservicepayments.mapper;

import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.ProductsSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.dto.PurchaseResponse;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSoldItem;
import java.util.List;
import pe.edu.cibertec.platonmicroservicepayments.model.ProductsSold;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductsSoldMapper {

    private final StateSoldMapper stateSoldMapper;

    public ProductsSold toEntity(ProductsSoldRequest request, StateSold state) {
        return ProductsSold.builder()
                .idProducts(request.idProducts())
                .idUser(request.idUser())
                .numberSold(request.numberSold())
                .stateSold(state)
                .build();
    }

    public void updateEntity(ProductsSoldRequest request, ProductsSold target, StateSold state) {
        target.setIdProducts(request.idProducts());
        target.setIdUser(request.idUser());
        target.setNumberSold(request.numberSold());
        target.setStateSold(state);
    }

    public ProductsSoldResponse toResponse(ProductsSold entity) {
        return new ProductsSoldResponse(
                entity.getIdProductsSold(),
                entity.getIdProducts(),
                entity.getIdUser(),
                entity.getNumberSold(),
                entity.getCreateDate(),
                stateSoldMapper.toResponse(entity.getStateSold()));
    }

    public PurchaseResponse toPurchaseResponse(ProductsSold entity) {
        var products = entity.getItems().isEmpty()
                ? List.of(new PurchaseResponse.Item(entity.getIdProducts(), entity.getNumberSold(), null, null))
                : entity.getItems().stream()
                    .map(item -> new PurchaseResponse.Item(item.getIdProducts(), item.getNumberSold(),
                            item.getUnitPrice(), item.subtotal())).toList();
        return new PurchaseResponse(entity.getIdProductsSold(), entity.getIdUser(), entity.getCreateDate(),
                stateSoldMapper.toResponse(entity.getStateSold()), products, entity.getTotalAmount());
    }

    /** Mantiene el contrato de eventos por producto usado por Products. */
    public ProductsSoldResponse toItemResponse(ProductsSold sale, ProductsSoldItem item) {
        return new ProductsSoldResponse(sale.getIdProductsSold(), item.getIdProducts(), sale.getIdUser(),
                item.getNumberSold(), sale.getCreateDate(), stateSoldMapper.toResponse(sale.getStateSold()));
    }
}
