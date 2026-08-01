package ch.junto.user;

import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import ch.junto.core.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findByIdOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public User getById(UUID id) {
        return findByIdOrThrow(id);
    }

    @Transactional
    public User createFromEmail(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.split("@")[0]); // Default, updated during onboarding
        user.setOnboarded(false);
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setInviteCodesRemaining(5);
        return userRepository.save(user);
    }

    @Transactional
    public User completeOnboarding(UUID userId, String displayName) {
        User user = findByIdOrThrow(userId);
        user.setDisplayName(displayName);
        user.setOnboarded(true);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(UUID userId, String displayName, String bio,
                              String city, Double locationLat, Double locationLng) {
        User user = findByIdOrThrow(userId);
        if (displayName != null) user.setDisplayName(displayName);
        if (bio != null) user.setBio(bio);
        if (city != null) user.setCity(city);
        if (locationLat != null) user.setLocationLat(locationLat);
        if (locationLng != null) user.setLocationLng(locationLng);
        return userRepository.save(user);
    }

    @Transactional
    public User updateLocation(UUID userId, double lat, double lng) {
        User user = findByIdOrThrow(userId);
        user.setLocationLat(lat);
        user.setLocationLng(lng);
        return userRepository.save(user);
    }

    @Transactional
    public User clearLocation(UUID userId) {
        User user = findByIdOrThrow(userId);
        user.setLocationLat(null);
        user.setLocationLng(null);
        return userRepository.save(user);
    }

    @Transactional
    public User suspendUser(UUID userId) {
        User user = findByIdOrThrow(userId);
        user.setStatus(UserStatus.SUSPENDED);
        return userRepository.save(user);
    }

    @Transactional
    public void anonymizeUser(UUID userId) {
        User user = findByIdOrThrow(userId);
        user.setEmail("deleted-" + userId + "@junto.app");
        user.setDisplayName("Deleted User");
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }

    public boolean isSuspended(String email) {
        return userRepository.findByEmail(email)
            .map(u -> u.getStatus() == UserStatus.SUSPENDED)
            .orElse(false);
    }
}
