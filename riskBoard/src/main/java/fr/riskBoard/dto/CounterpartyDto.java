package fr.riskBoard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CounterpartyDto {
    private Long id;
    private String name;
    private String ricosCode;
    private String country;
    private String sector;
}
