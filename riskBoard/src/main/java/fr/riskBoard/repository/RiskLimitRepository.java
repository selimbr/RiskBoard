package fr.riskBoard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import fr.riskBoard.enums.LimitType;
import fr.riskBoard.entities.RiskLimit;

public interface RiskLimitRepository extends JpaRepository<RiskLimit, Long> {

    Optional<RiskLimit> findByCounterpartyIdAndLimitType(Long counterpartyId, LimitType limitType);

    List<RiskLimit> findByLimitType(LimitType limitType);

    @Query("select rl from RiskLimit rl join fetch rl.counterparty")
    List<RiskLimit> findAllWithCounterparty();
}
