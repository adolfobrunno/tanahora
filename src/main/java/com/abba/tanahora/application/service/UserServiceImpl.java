package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.Plan;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User register(String whatsappId, String nome) {
        return userRepository.findByWhatsappId(whatsappId)
                .map(existing -> updateNameIfProvided(existing, nome))
                .orElseGet(() -> createNewUser(whatsappId, nome));
    }

    @Override
    public void upgradePro(String whatsappId) {
        applyPremiumCycle(whatsappId, 1);
    }

    @Override
    public void applyPremiumCycle(String whatsappId, int months) {
        User user = userRepository.findByWhatsappId(whatsappId).orElseGet(() -> createNewUser(whatsappId, null));
        user.applyPremiumCycle(months);
        userRepository.save(user);
    }

    @Override
    public void downgradeToFree(String whatsappId) {
        userRepository.findByWhatsappId(whatsappId).ifPresent(user -> {
            user.downgradeToFree();
            userRepository.save(user);
        });
    }

    @Override
    public User findByWhatsappId(String whatsappId) {
        return userRepository.findByWhatsappId(whatsappId).orElse(null);
    }

    private User updateNameIfProvided(User user, String name) {
        if (name != null && !name.isBlank()) {
            user.setName(name);
            userRepository.save(user);
        }
        return user;
    }

    private User createNewUser(String whatsappId, String name) {
        User user = new User();
        user.setWhatsappId(whatsappId);
        user.setName(name);
        user.setPlan(Plan.FREE);
        return userRepository.save(user);
    }
}
