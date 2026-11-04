package com.shopsphere.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.notification.entity.Notification;
import com.shopsphere.notification.repository.NotificationRepository;
import com.shopsphere.user.repository.UserRepository;

/**
 * Notifies a user from inside service flows. REQUIRES_NEW so a notification
 * failure never rolls back the caller — notifications are best-effort writes.
 */
@Service
public class NotificationSaver {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationSaver(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void orderCreated(Long userId, String title, String body) {
        save(userId, Notification.TYPE_ORDER, title, body);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stockAlert(Long userId, String title, String body) {
        save(userId, Notification.TYPE_STOCK, title, body);
    }

    private void save(Long userId, String type, String title, String body) {
        try {
            notificationRepository.save(Notification.builder()
                    .user(userRepository.getReferenceById(userId))
                    .type(type)
                    .title(title)
                    .body(body)
                    .build());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationSaver.class)
                    .warn("Notification write failed (non-fatal): {}", e.getMessage());
        }
    }
}
