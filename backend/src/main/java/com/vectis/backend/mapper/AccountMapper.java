package com.vectis.backend.mapper;

import com.vectis.backend.domain.entity.Account;
import com.vectis.backend.dto.AccountResponse;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .kind(account.getKind())
                .detail(account.getDetail())
                .ccy(account.getCcy())
                .balance(account.getBalance())
                .remunerada(account.isRemunerada())
                .tna(account.getTna())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
