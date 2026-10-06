package pe.edu.cibertec.platonmicroservicepayments.mapper;

import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldRequest;
import pe.edu.cibertec.platonmicroservicepayments.dto.StateSoldResponse;
import pe.edu.cibertec.platonmicroservicepayments.model.StateSold;
import org.springframework.stereotype.Component;

@Component
public class StateSoldMapper {

    public StateSold toEntity(StateSoldRequest request) {
        return StateSold.builder()
                .nameState(request.nameState().trim())
                .build();
    }

    public void updateEntity(StateSoldRequest request, StateSold target) {
        target.setNameState(request.nameState().trim());
    }

    public StateSoldResponse toResponse(StateSold entity) {
        return new StateSoldResponse(entity.getIdStateSold(), entity.getNameState());
    }
}
