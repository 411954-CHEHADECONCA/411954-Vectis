package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.dto.AccountResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account, BigDecimal derivedTna) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .kind(account.getKind())
                .detail(account.getDetail())
                .ccy(account.getCcy())
                .balance(account.getBalance())
                .computedBalance(null)
                .remunerada(derivedTna != null)
                .tna(derivedTna)
                .includeInCashflow(account.isIncludeInCashflow())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
