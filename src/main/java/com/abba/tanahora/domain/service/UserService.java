package com.abba.tanahora.domain.service;

import com.abba.tanahora.domain.model.User;

public interface UserService {

    User register(String whatsappId, String nome);
    void upgradePro(String whatsappId);

    void applyPremiumCycle(String whatsappId, int months);

    void downgradeToFree(String whatsappId);

    User findByWhatsappId(String whatsappId);

}
