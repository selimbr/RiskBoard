package fr.riskBoard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.riskBoard.entities.DerogationRequest;
import fr.riskBoard.enums.DerogationStatus;

public interface DerogationRequestRepository extends JpaRepository<DerogationRequest, Long> {

    @Query("select d from DerogationRequest d join fetch d.counterparty where d.status = :status")
    List<DerogationRequest> findByStatus(@Param("status") DerogationStatus status);
}
